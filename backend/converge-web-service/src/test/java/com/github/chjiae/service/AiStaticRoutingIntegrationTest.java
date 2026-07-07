package com.github.chjiae.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.chjiae.contract.gateway.GatewaySnapshotJson;
import com.github.chjiae.contract.gateway.GatewaySnapshotManifest;
import com.github.chjiae.contract.gateway.GatewaySnapshotRedisKeys;
import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;
import com.github.chjiae.service.entity.ai.AiGatewaySnapshotOutbox;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 静态路由控制面集成测试。
 * 覆盖租户隔离、精确模型绑定、拓扑启用校验、预览和 V2 快照发布。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiStaticRoutingIntegrationTest extends BaseIntegrationTest {

    /** 测试唯一后缀 */
    private static final String RUN_ID = Long.toString(System.currentTimeMillis());

    /** 明文密钥前缀，避免断言输出完整密钥 */
    private static final String SECRET_PREFIX = "phase05";

    private static String adminToken;
    private static String ownerTokenA;
    private static String ownerTokenB;
    private static String memberTokenA;
    private static Long tenantIdA;
    private static Long providerAId;
    private static Long connectionAId;
    private static Long modelAId;
    private static Long resourceAId;
    private static Long drainingResourceId;
    private static Long poolId;
    private static Long policyId;
    private static Long tenantBResourceId;

    @Autowired
    private AiGatewaySnapshotOutboxMapper outboxMapper;

    @Autowired
    private GatewaySnapshotOutboxProjector projector;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @Order(1)
    void setup_创建租户资源和基础目录() {
        adminToken = login("admin", "admin123");
        ownerTokenA = createTenantAndLoginOwner("route_a_" + RUN_ID,
                "route_owner_a_" + RUN_ID, "route_owner_a_" + RUN_ID + "@test.com");
        ownerTokenB = createTenantAndLoginOwner("route_b_" + RUN_ID,
                "route_owner_b_" + RUN_ID, "route_owner_b_" + RUN_ID + "@test.com");
        memberTokenA = createUserAndAssignRole(ownerTokenA,
                "route_member_" + RUN_ID, "route_member_" + RUN_ID + "@test.com", "TENANT_MEMBER");

        JsonNode provider = assertSuccess(post("/api/v1/ai/providers", ownerTokenA,
                providerBody("openai_" + RUN_ID)));
        providerAId = provider.get("id").asLong();
        tenantIdA = provider.get("tenantId").asLong();
        connectionAId = assertSuccess(post("/api/v1/ai/providers/" + providerAId + "/connections",
                ownerTokenA, connectionBody("conn_" + RUN_ID))).get("id").asLong();
        modelAId = assertSuccess(post("/api/v1/ai/models", ownerTokenA,
                modelBody("public_chat_" + RUN_ID))).get("id").asLong();
        Long credentialId = assertSuccess(post("/api/v1/ai/providers/" + providerAId + "/credentials",
                ownerTokenA, credentialBody("cred_" + RUN_ID, upstreamSecret()))).get("id").asLong();
        Long drainingCredentialId = assertSuccess(post("/api/v1/ai/providers/" + providerAId + "/credentials",
                ownerTokenA, credentialBody("cred_drain_" + RUN_ID, upstreamSecret() + "_drain"))).get("id").asLong();
        resourceAId = assertSuccess(post("/api/v1/ai/resources", ownerTokenA,
                resourceBody(connectionAId, credentialId, "res_a_" + RUN_ID))).get("id").asLong();
        drainingResourceId = assertSuccess(post("/api/v1/ai/resources", ownerTokenA,
                resourceBody(connectionAId, drainingCredentialId, "res_drain_" + RUN_ID))).get("id").asLong();
        assertSuccess(post("/api/v1/ai/resources/" + drainingResourceId + "/drain", ownerTokenA, "{}"));

        JsonNode providerB = assertSuccess(post("/api/v1/ai/providers", ownerTokenB,
                providerBody("openai_b_" + RUN_ID)));
        Long connectionB = assertSuccess(post("/api/v1/ai/providers/" + providerB.get("id").asLong() + "/connections",
                ownerTokenB, connectionBody("conn_b_" + RUN_ID))).get("id").asLong();
        Long credentialB = assertSuccess(post("/api/v1/ai/providers/" + providerB.get("id").asLong() + "/credentials",
                ownerTokenB, credentialBody("cred_b_" + RUN_ID, upstreamSecret() + "_b"))).get("id").asLong();
        tenantBResourceId = assertSuccess(post("/api/v1/ai/resources", ownerTokenB,
                resourceBody(connectionB, credentialB, "res_b_" + RUN_ID))).get("id").asLong();
    }

    @Test
    @Order(2)
    void resourcePool_成员权限租户和重复关系校验() {
        assertThat(post("/api/v1/ai/resource-pools", memberTokenA, poolBody("member_pool_" + RUN_ID))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode pool = assertSuccess(post("/api/v1/ai/resource-pools", ownerTokenA,
                poolBody("primary_pool_" + RUN_ID)));
        poolId = pool.get("id").asLong();

        JsonNode member = assertSuccess(post("/api/v1/ai/resource-pools/" + poolId + "/members", ownerTokenA,
                memberBody(resourceAId, 100, 70)));
        assertThat(member.get("priority").asInt()).isEqualTo(100);

        ResponseEntity<String> duplicate = post("/api/v1/ai/resource-pools/" + poolId + "/members",
                ownerTokenA, memberBody(resourceAId, 100, 70));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(duplicate, 400);

        ResponseEntity<String> crossTenant = post("/api/v1/ai/resource-pools/" + poolId + "/members",
                ownerTokenA, memberBody(tenantBResourceId, 100, 100));
        assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(crossTenant, 404);
    }

    @Test
    @Order(3)
    void binding_仅允许精确模型映射并写入Outbox() {
        JsonNode binding = assertSuccess(post("/api/v1/ai/resource-model-bindings", ownerTokenA,
                bindingBody(resourceAId, modelAId, "CHAT_COMPLETIONS", "gpt-4.1-upstream")));
        assertThat(binding.get("upstreamModelName").asText()).isEqualTo("gpt-4.1-upstream");

        ResponseEntity<String> wildcard = post("/api/v1/ai/resource-model-bindings", ownerTokenA,
                bindingBody(resourceAId, modelAId, "CHAT_COMPLETIONS", "*"));
        assertThat(wildcard.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(wildcard, 400);

        List<AiGatewaySnapshotOutbox> outboxes = outboxMapper.selectList(
                new LambdaQueryWrapper<AiGatewaySnapshotOutbox>()
                        .eq(AiGatewaySnapshotOutbox::getTenantId, tenantIdA)
                        .eq(AiGatewaySnapshotOutbox::getChangeType, "AI_RESOURCE_MODEL_BINDING_CHANGED"));
        assertThat(outboxes).isNotEmpty();
    }

    @Test
    @Order(4)
    void routePolicy_草稿启用前校验静态拓扑且预览不执行上游请求() {
        JsonNode policy = assertSuccess(post("/api/v1/ai/route-policies", ownerTokenA,
                policyBody("policy_" + RUN_ID, modelAId, "CHAT_COMPLETIONS")));
        policyId = policy.get("id").asLong();
        assertThat(policy.get("adminStatus").asText()).isEqualTo("DRAFT");

        assertSuccess(post("/api/v1/ai/route-policies/" + policyId + "/targets", ownerTokenA,
                targetBody(poolId, 100, 100)));
        JsonNode enabled = assertSuccess(post("/api/v1/ai/route-policies/" + policyId + "/enable", ownerTokenA, "{}"));
        assertThat(enabled.get("adminStatus").asText()).isEqualTo("ENABLED");

        JsonNode preview = assertSuccess(post("/api/v1/ai/routes/preview", ownerTokenA,
                previewBody("public_chat_" + RUN_ID, "CHAT_COMPLETIONS", "seed-route")));
        assertThat(preview.get("validationStatus").asText()).isEqualTo("VALID");
        assertThat(preview.get("warnings").toString()).contains("STATIC_CONFIGURATION_ONLY");
        assertThat(preview.get("warnings").toString()).contains("DYNAMIC_STATE_NOT_APPLIED");
        assertThat(preview.get("warnings").toString()).contains("NO_UPSTREAM_REQUEST_EXECUTED");
        assertThat(preview.toString()).doesNotContain(upstreamSecret());
    }

    @Test
    @Order(5)
    void routePolicy_只有Draining资源时不能启用() {
        Long drainPool = assertSuccess(post("/api/v1/ai/resource-pools", ownerTokenA,
                poolBody("drain_pool_" + RUN_ID))).get("id").asLong();
        assertSuccess(post("/api/v1/ai/resource-pools/" + drainPool + "/members", ownerTokenA,
                memberBody(drainingResourceId, 100, 100)));
        assertSuccess(post("/api/v1/ai/resource-model-bindings", ownerTokenA,
                bindingBody(drainingResourceId, modelAId, "RESPONSES", "draining-upstream")));
        Long drainPolicy = assertSuccess(post("/api/v1/ai/route-policies", ownerTokenA,
                policyBody("drain_policy_" + RUN_ID, modelAId, "RESPONSES"))).get("id").asLong();
        assertSuccess(post("/api/v1/ai/route-policies/" + drainPolicy + "/targets", ownerTokenA,
                targetBody(drainPool, 100, 100)));

        ResponseEntity<String> enable = post("/api/v1/ai/route-policies/" + drainPolicy + "/enable", ownerTokenA, "{}");
        assertThat(enable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(enable, 400);
    }

    @Test
    @Order(6)
    void snapshot_控制面发布V2且不包含明文密钥() {
        GatewaySnapshotOutboxProjector.ProjectorResult result = projector.projectPendingOnce("phase05-test");
        assertThat(result.publishedTenantCount()).isGreaterThanOrEqualTo(1);

        String manifestJson = redisTemplate.opsForValue()
                .get(GatewaySnapshotRedisKeys.currentManifestKey(tenantIdA.toString()));
        GatewaySnapshotManifest manifest = GatewaySnapshotJson.fromJson(manifestJson, GatewaySnapshotManifest.class);
        String payloadJson = redisTemplate.opsForValue().get(manifest.payloadRedisKey());
        assertThat(payloadJson).doesNotContain(upstreamSecret());

        GatewayTenantSnapshot snapshot = GatewaySnapshotJson.fromJson(payloadJson, GatewayTenantSnapshot.class);
        assertThat(snapshot.schemaVersion()).isEqualTo(3);
        assertThat(snapshot.resourcePools()).isNotEmpty();
        assertThat(snapshot.resourceModelBindings()).isNotEmpty();
        assertThat(snapshot.routePolicies()).isNotEmpty();
    }

    @Test
    @Order(7)
    void api_不提供硬删除接口() {
        assertDeleteNotSupported(delete("/api/v1/ai/resource-pools/" + poolId, ownerTokenA));
        assertDeleteNotSupported(delete("/api/v1/ai/resource-model-bindings/1", ownerTokenA));
        assertDeleteNotSupported(delete("/api/v1/ai/route-policies/" + policyId, ownerTokenA));
        assertDeleteNotSupported(delete("/api/v1/ai/route-targets/1", ownerTokenA));
    }

    private String createTenantAndLoginOwner(String tenantCode, String adminUsername, String adminEmail) {
        String body = """
                {
                  "code": "%s",
                  "name": "%s",
                  "description": "静态路由测试租户",
                  "adminUsername": "%s",
                  "adminEmail": "%s",
                  "adminPassword": "test123456"
                }
                """.formatted(tenantCode, tenantCode, adminUsername, adminEmail);
        assertSuccess(post("/api/v1/tenants", adminToken, body));
        return login(adminUsername, "test123456");
    }

    private String createUserAndAssignRole(String ownerToken, String username, String email, String roleCode) {
        String userBody = """
                {"username":"%s","email":"%s","password":"test123456"}
                """.formatted(username, email);
        JsonNode user = assertSuccess(post("/api/v1/users", ownerToken, userBody));
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

    private void assertDeleteNotSupported(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(parseJson(response.getBody()).get("code").asInt()).isNotEqualTo(200);
    }

    private String providerBody(String code) {
        return """
                {"code":"%s","displayName":"供应商","providerKind":"OPENAI","status":"ENABLED","description":"测试"}
                """.formatted(code);
    }

    private String connectionBody(String code) {
        return """
                {"code":"%s","displayName":"连接","protocolType":"OPENAI_COMPATIBLE","baseUrl":"https://api.example.test/v1","status":"ENABLED","description":"测试"}
                """.formatted(code);
    }

    private String modelBody(String code) {
        return """
                {"code":"%s","displayName":"公开模型","modelFamily":"chat","status":"ENABLED","description":"测试"}
                """.formatted(code);
    }

    private String credentialBody(String code, String apiKey) {
        return """
                {"code":"%s","displayName":"凭据","description":"测试","apiKey":"%s"}
                """.formatted(code, apiKey);
    }

    private String resourceBody(Long connectionId, Long credentialId, String code) {
        return """
                {"upstreamConnectionId":%d,"credentialId":%d,"code":"%s","displayName":"资源","description":"测试","adminStatus":"ENABLED"}
                """.formatted(connectionId, credentialId, code);
    }

    private String poolBody(String code) {
        return """
                {"code":"%s","displayName":"资源池","description":"测试资源池","adminStatus":"ENABLED"}
                """.formatted(code);
    }

    private String memberBody(Long resourceId, int priority, int weight) {
        return """
                {"executionResourceId":%d,"adminStatus":"ENABLED","priority":%d,"weight":%d}
                """.formatted(resourceId, priority, weight);
    }

    private String bindingBody(Long resourceId, Long modelId, String operation, String upstreamModel) {
        return """
                {"executionResourceId":%d,"publicModelId":%d,"canonicalOperation":"%s","upstreamModelName":"%s","adminStatus":"ENABLED"}
                """.formatted(resourceId, modelId, operation, upstreamModel);
    }

    private String policyBody(String name, Long modelId, String operation) {
        return """
                {"publicModelId":%d,"canonicalOperation":"%s","displayName":"%s","description":"测试策略"}
                """.formatted(modelId, operation, name);
    }

    private String targetBody(Long targetPoolId, int priority, int weight) {
        return """
                {"resourcePoolId":%d,"adminStatus":"ENABLED","priority":%d,"weight":%d}
                """.formatted(targetPoolId, priority, weight);
    }

    private String previewBody(String publicModelCode, String operation, String seed) {
        return """
                {"publicModelCode":"%s","canonicalOperation":"%s","selectionSeed":"%s"}
                """.formatted(publicModelCode, operation, seed);
    }

    private String upstreamSecret() {
        return SECRET_PREFIX + "-" + RUN_ID + "-secret-value";
    }
}
