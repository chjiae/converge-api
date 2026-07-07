package com.github.chjiae.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.chjiae.contract.gateway.GatewayClientKeyCrypto;
import com.github.chjiae.contract.gateway.GatewaySnapshotJson;
import com.github.chjiae.contract.gateway.GatewaySnapshotManifest;
import com.github.chjiae.contract.gateway.GatewaySnapshotRedisKeys;
import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;
import com.github.chjiae.service.entity.ai.AiClientApiKey;
import com.github.chjiae.service.entity.ai.AiGatewaySnapshotOutbox;
import com.github.chjiae.service.mapper.ai.AiClientApiKeyMapper;
import com.github.chjiae.service.mapper.ai.AiGatewaySnapshotOutboxMapper;
import com.github.chjiae.service.service.ai.GatewaySnapshotOutboxProjector;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 下游 Client API Key 与访问组控制面集成测试。
 * 覆盖一次性明文返回、verifier 存储、状态流转、租户隔离、授权并集和 V3 快照。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiClientAccessIntegrationTest extends BaseIntegrationTest {

    /** 测试唯一后缀 */
    private static final String RUN_ID = Long.toString(System.currentTimeMillis());

    /** 仅用于断言泄露扫描的高熵前缀 */
    private static final String UPSTREAM_SECRET_PREFIX = "phase06-upstream";

    private static String ownerTokenA;
    private static String ownerTokenB;
    private static String memberTokenA;
    private static Long tenantIdA;
    private static Long modelAId;
    private static Long modelBId;
    private static Long groupAId;
    private static Long groupBId;
    private static Long keyId;
    private static Long bindingId;
    private static String rawClientKey;
    private static String rotatedRawClientKey;

    @Autowired
    private AiClientApiKeyMapper clientApiKeyMapper;

    @Autowired
    private AiGatewaySnapshotOutboxMapper outboxMapper;

    @Autowired
    private GatewaySnapshotOutboxProjector projector;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @Order(1)
    void setup_创建静态路由基础数据() {
        String adminToken = login("admin", "admin123");
        ownerTokenA = createTenantAndLoginOwner(adminToken, "access_a_" + RUN_ID,
                "access_owner_a_" + RUN_ID, "access_owner_a_" + RUN_ID + "@test.com");
        ownerTokenB = createTenantAndLoginOwner(adminToken, "access_b_" + RUN_ID,
                "access_owner_b_" + RUN_ID, "access_owner_b_" + RUN_ID + "@test.com");
        memberTokenA = createUserAndAssignRole(ownerTokenA,
                "access_member_" + RUN_ID, "access_member_" + RUN_ID + "@test.com", "TENANT_MEMBER");

        JsonNode provider = assertSuccess(post("/api/v1/ai/providers", ownerTokenA,
                providerBody("openai_" + RUN_ID)));
        tenantIdA = provider.get("tenantId").asLong();
        long providerId = provider.get("id").asLong();
        long connectionId = assertSuccess(post("/api/v1/ai/providers/" + providerId + "/connections",
                ownerTokenA, connectionBody("conn_" + RUN_ID))).get("id").asLong();
        modelAId = assertSuccess(post("/api/v1/ai/models", ownerTokenA,
                modelBody("public_chat_" + RUN_ID))).get("id").asLong();
        modelBId = assertSuccess(post("/api/v1/ai/models", ownerTokenA,
                modelBody("public_responses_" + RUN_ID))).get("id").asLong();
        long credentialId = assertSuccess(post("/api/v1/ai/providers/" + providerId + "/credentials",
                ownerTokenA, credentialBody("cred_" + RUN_ID, upstreamSecret()))).get("id").asLong();
        long resourceId = assertSuccess(post("/api/v1/ai/resources", ownerTokenA,
                resourceBody(connectionId, credentialId, "res_" + RUN_ID))).get("id").asLong();
        long poolId = assertSuccess(post("/api/v1/ai/resource-pools", ownerTokenA,
                poolBody("pool_" + RUN_ID))).get("id").asLong();
        assertSuccess(post("/api/v1/ai/resource-pools/" + poolId + "/members", ownerTokenA,
                memberBody(resourceId, 100, 100)));
        assertSuccess(post("/api/v1/ai/resource-model-bindings", ownerTokenA,
                bindingBody(resourceId, modelAId, "CHAT_COMPLETIONS", "upstream-chat")));
        long policyId = assertSuccess(post("/api/v1/ai/route-policies", ownerTokenA,
                policyBody("policy_" + RUN_ID, modelAId, "CHAT_COMPLETIONS"))).get("id").asLong();
        assertSuccess(post("/api/v1/ai/route-policies/" + policyId + "/targets", ownerTokenA,
                targetBody(poolId, 100, 100)));
        assertSuccess(post("/api/v1/ai/route-policies/" + policyId + "/enable", ownerTokenA, "{}"));
    }

    @Test
    @Order(2)
    void accessGroup_角色租户精确授权和并集关系校验() {
        assertThat(post("/api/v1/ai/access-groups", memberTokenA, accessGroupBody("member_" + RUN_ID))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        groupAId = assertSuccess(post("/api/v1/ai/access-groups", ownerTokenA,
                accessGroupBody("chat_group_" + RUN_ID))).get("id").asLong();
        groupBId = assertSuccess(post("/api/v1/ai/access-groups", ownerTokenA,
                accessGroupBody("responses_group_" + RUN_ID))).get("id").asLong();

        JsonNode grantA = assertSuccess(post("/api/v1/ai/access-groups/" + groupAId + "/model-grants",
                ownerTokenA, grantBody(modelAId, "CHAT_COMPLETIONS")));
        assertThat(grantA.get("canonicalOperation").asText()).isEqualTo("CHAT_COMPLETIONS");
        assertSuccess(post("/api/v1/ai/access-groups/" + groupBId + "/model-grants",
                ownerTokenA, grantBody(modelBId, "RESPONSES")));

        ResponseEntity<String> wildcard = post("/api/v1/ai/access-groups/" + groupAId + "/model-grants",
                ownerTokenA, "{\"publicModelId\":" + modelAId + ",\"canonicalOperation\":\"*\",\"adminStatus\":\"ENABLED\"}");
        assertThat(wildcard.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> crossTenant = post("/api/v1/ai/access-groups/" + groupAId + "/model-grants",
                ownerTokenA, grantBody(createOtherTenantModel(), "CHAT_COMPLETIONS"));
        assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(crossTenant, 404);
    }

    @Test
    @Order(3)
    void clientKey_只一次返回明文并以Verifier存储() {
        ResponseEntity<String> created = post("/api/v1/ai/client-api-keys", ownerTokenA,
                clientKeyBody("key_" + RUN_ID, Instant.now().plusSeconds(3600)));
        assertThat(created.getHeaders().getCacheControl()).contains("no-store");
        JsonNode data = assertSuccess(created);
        rawClientKey = data.get("rawKey").asText();
        keyId = data.get("id").asLong();
        assertThat(rawClientKey).startsWith("cvg_live_");
        assertThat(GatewayClientKeyCrypto.parse(rawClientKey).valid()).isTrue();

        JsonNode detail = assertSuccess(get("/api/v1/ai/client-api-keys/" + keyId, ownerTokenA));
        assertThat(detail.has("rawKey")).isFalse();
        assertThat(detail.has("secretVerifierHash")).isFalse();
        assertThat(detail.has("secretVerifierSalt")).isFalse();

        AiClientApiKey stored = clientApiKeyMapper.selectById(keyId);
        assertThat(stored.getSecretVerifierSalt()).hasSize(16);
        assertThat(stored.getSecretVerifierHash()).hasSize(32);
        assertThat(new String(stored.getSecretVerifierHash(), StandardCharsets.UTF_8)).doesNotContain(rawClientKey);
        assertThat(GatewayClientKeyCrypto.verify(rawClientKey, stored.getKeyId(), stored.getKeyVersion(),
                stored.getSecretVerifierSalt(), stored.getSecretVerifierHash())).isTrue();
    }

    @Test
    @Order(4)
    void clientKey_绑定多组授权并写入Outbox() {
        bindingId = assertSuccess(post("/api/v1/ai/client-api-keys/" + keyId + "/access-groups",
                ownerTokenA, keyGroupBody(groupAId))).get("id").asLong();
        assertSuccess(post("/api/v1/ai/client-api-keys/" + keyId + "/access-groups",
                ownerTokenA, keyGroupBody(groupBId)));

        List<AiGatewaySnapshotOutbox> outboxes = outboxMapper.selectList(
                new LambdaQueryWrapper<AiGatewaySnapshotOutbox>()
                        .eq(AiGatewaySnapshotOutbox::getTenantId, tenantIdA)
                        .in(AiGatewaySnapshotOutbox::getChangeType,
                                "AI_ACCESS_GROUP_CHANGED",
                                "AI_ACCESS_GROUP_MODEL_GRANT_CHANGED",
                                "AI_CLIENT_API_KEY_CHANGED",
                                "AI_CLIENT_API_KEY_ACCESS_GROUP_CHANGED"));
        assertThat(outboxes).isNotEmpty();
    }

    @Test
    @Order(5)
    void clientKey_轮换停用撤销过期语义正确() {
        ResponseEntity<String> rotated = post("/api/v1/ai/client-api-keys/" + keyId + "/rotate", ownerTokenA, "{}");
        assertThat(rotated.getHeaders().getCacheControl()).contains("no-store");
        JsonNode rotatedData = assertSuccess(rotated);
        rotatedRawClientKey = rotatedData.get("rawKey").asText();
        assertThat(rotatedRawClientKey).startsWith("cvg_live_");
        assertThat(rotatedRawClientKey).isNotEqualTo(rawClientKey);
        AiClientApiKey stored = clientApiKeyMapper.selectById(keyId);
        assertThat(stored.getKeyVersion()).isEqualTo(2);
        assertThat(GatewayClientKeyCrypto.verify(rawClientKey, stored.getKeyId(), stored.getKeyVersion(),
                stored.getSecretVerifierSalt(), stored.getSecretVerifierHash())).isFalse();
        assertThat(GatewayClientKeyCrypto.verify(rotatedRawClientKey, stored.getKeyId(), stored.getKeyVersion(),
                stored.getSecretVerifierSalt(), stored.getSecretVerifierHash())).isTrue();

        assertSuccess(post("/api/v1/ai/client-api-keys/" + keyId + "/disable", ownerTokenA, "{}"));
        assertSuccess(post("/api/v1/ai/client-api-keys/" + keyId + "/enable", ownerTokenA, "{}"));
        assertSuccess(post("/api/v1/ai/client-api-keys/" + keyId + "/revoke", ownerTokenA, "{}"));
        ResponseEntity<String> enableRevoked = post("/api/v1/ai/client-api-keys/" + keyId + "/enable",
                ownerTokenA, "{}");
        assertThat(enableRevoked.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> rotateRevoked = post("/api/v1/ai/client-api-keys/" + keyId + "/rotate",
                ownerTokenA, "{}");
        assertThat(rotateRevoked.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(6)
    void snapshot_发布V3且不包含明文ClientKey() {
        GatewaySnapshotOutboxProjector.ProjectorResult result = projector.projectPendingOnce("phase06-test");
        assertThat(result.publishedTenantCount()).isGreaterThanOrEqualTo(1);

        String manifestJson = redisTemplate.opsForValue()
                .get(GatewaySnapshotRedisKeys.currentManifestKey(tenantIdA.toString()));
        GatewaySnapshotManifest manifest = GatewaySnapshotJson.fromJson(manifestJson, GatewaySnapshotManifest.class);
        String payloadJson = redisTemplate.opsForValue().get(manifest.payloadRedisKey());
        assertThat(payloadJson).doesNotContain(rawClientKey);
        assertThat(payloadJson).doesNotContain(rotatedRawClientKey);
        assertThat(payloadJson).doesNotContain(upstreamSecret());

        GatewayTenantSnapshot snapshot = GatewaySnapshotJson.fromJson(payloadJson, GatewayTenantSnapshot.class);
        assertThat(snapshot.schemaVersion()).isEqualTo(3);
        assertThat(snapshot.accessGroups()).isNotEmpty();
        assertThat(snapshot.accessGroupModelGrants()).isNotEmpty();
        assertThat(snapshot.clientApiKeys()).isNotEmpty();
        assertThat(snapshot.clientApiKeyAccessGroups()).isNotEmpty();
    }

    @Test
    @Order(7)
    void api_不提供硬删除接口() {
        assertDeleteNotSupported(delete("/api/v1/ai/access-groups/" + groupAId, ownerTokenA));
        assertDeleteNotSupported(delete("/api/v1/ai/access-group-model-grants/1", ownerTokenA));
        assertDeleteNotSupported(delete("/api/v1/ai/client-api-keys/" + keyId, ownerTokenA));
        assertDeleteNotSupported(delete("/api/v1/ai/client-api-key-access-groups/" + bindingId, ownerTokenA));
    }

    private String createTenantAndLoginOwner(String adminToken, String tenantCode, String username, String email) {
        String body = """
                {"code":"%s","name":"%s","description":"访问组测试租户","adminUsername":"%s","adminEmail":"%s","adminPassword":"test123456"}
                """.formatted(tenantCode, tenantCode, username, email);
        assertSuccess(post("/api/v1/tenants", adminToken, body));
        return login(username, "test123456");
    }

    private String createUserAndAssignRole(String ownerToken, String username, String email, String roleCode) {
        JsonNode user = assertSuccess(post("/api/v1/users", ownerToken,
                "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"test123456\"}"));
        long roleId = findRoleId(ownerToken, roleCode);
        assertSuccess(put("/api/v1/users/" + user.get("id").asLong() + "/roles", ownerToken,
                "{\"roleIds\":[" + roleId + "]}"));
        return login(username, "test123456");
    }

    private long findRoleId(String token, String roleCode) {
        JsonNode roles = assertSuccess(get("/api/v1/roles", token));
        for (JsonNode role : roles) {
            if (roleCode.equals(role.get("code").asText())) {
                return role.get("id").asLong();
            }
        }
        throw new AssertionError("未找到角色: " + roleCode);
    }

    private Long createOtherTenantModel() {
        JsonNode providerB = assertSuccess(post("/api/v1/ai/providers", ownerTokenB,
                providerBody("provider_b_" + RUN_ID)));
        assertThat(providerB.get("id").asLong()).isGreaterThan(0);
        return assertSuccess(post("/api/v1/ai/models", ownerTokenB,
                modelBody("other_model_" + RUN_ID))).get("id").asLong();
    }

    private void assertDeleteNotSupported(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(parseJson(response.getBody()).get("code").asInt()).isNotEqualTo(200);
    }

    private String providerBody(String code) {
        return "{\"code\":\"" + code + "\",\"displayName\":\"供应商\",\"providerKind\":\"OPENAI\",\"status\":\"ENABLED\",\"description\":\"测试\"}";
    }

    private String connectionBody(String code) {
        return "{\"code\":\"" + code + "\",\"displayName\":\"连接\",\"protocolType\":\"OPENAI_COMPATIBLE\",\"baseUrl\":\"https://api.example.test/v1\",\"status\":\"ENABLED\",\"description\":\"测试\"}";
    }

    private String modelBody(String code) {
        return "{\"code\":\"" + code + "\",\"displayName\":\"公开模型\",\"modelFamily\":\"chat\",\"status\":\"ENABLED\",\"description\":\"测试\"}";
    }

    private String credentialBody(String code, String secret) {
        return "{\"code\":\"" + code + "\",\"displayName\":\"凭据\",\"apiKey\":\"" + secret + "\",\"description\":\"测试\"}";
    }

    private String resourceBody(Long connectionId, Long credentialId, String code) {
        return "{\"code\":\"" + code + "\",\"displayName\":\"资源\",\"upstreamConnectionId\":" + connectionId
                + ",\"credentialId\":" + credentialId + ",\"adminStatus\":\"ENABLED\",\"description\":\"测试\"}";
    }

    private String poolBody(String code) {
        return "{\"code\":\"" + code + "\",\"displayName\":\"资源池\",\"adminStatus\":\"ENABLED\",\"description\":\"测试\"}";
    }

    private String memberBody(Long resourceId, int priority, int weight) {
        return "{\"executionResourceId\":" + resourceId + ",\"adminStatus\":\"ENABLED\",\"priority\":" + priority
                + ",\"weight\":" + weight + "}";
    }

    private String bindingBody(Long resourceId, Long modelId, String operation, String upstreamModel) {
        return "{\"executionResourceId\":" + resourceId + ",\"publicModelId\":" + modelId
                + ",\"canonicalOperation\":\"" + operation + "\",\"upstreamModelName\":\"" + upstreamModel
                + "\",\"adminStatus\":\"ENABLED\"}";
    }

    private String policyBody(String name, Long modelId, String operation) {
        return "{\"displayName\":\"" + name + "\",\"publicModelId\":" + modelId
                + ",\"canonicalOperation\":\"" + operation + "\",\"description\":\"测试\"}";
    }

    private String targetBody(Long poolId, int priority, int weight) {
        return "{\"resourcePoolId\":" + poolId + ",\"adminStatus\":\"ENABLED\",\"priority\":" + priority
                + ",\"weight\":" + weight + "}";
    }

    private String accessGroupBody(String code) {
        return "{\"code\":\"" + code + "\",\"displayName\":\"访问组\",\"adminStatus\":\"ENABLED\",\"description\":\"测试\"}";
    }

    private String grantBody(Long modelId, String operation) {
        return "{\"publicModelId\":" + modelId + ",\"canonicalOperation\":\"" + operation
                + "\",\"adminStatus\":\"ENABLED\"}";
    }

    private String clientKeyBody(String displayName, Instant expiresAt) {
        return "{\"displayName\":\"" + displayName + "\",\"description\":\"测试 Key\",\"expiresAt\":\""
                + expiresAt + "\"}";
    }

    private String keyGroupBody(Long groupId) {
        return "{\"accessGroupId\":" + groupId + ",\"adminStatus\":\"ENABLED\"}";
    }

    private String upstreamSecret() {
        return UPSTREAM_SECRET_PREFIX + "-" + RUN_ID + "-abcdefghijklmnopqrstuvwxyz1234567890";
    }
}
