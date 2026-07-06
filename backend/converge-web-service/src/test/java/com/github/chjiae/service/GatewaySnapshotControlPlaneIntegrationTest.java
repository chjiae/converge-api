package com.github.chjiae.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.chjiae.contract.gateway.GatewaySnapshotJson;
import com.github.chjiae.contract.gateway.GatewaySnapshotManifest;
import com.github.chjiae.contract.gateway.GatewaySnapshotRedisKeys;
import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;
import com.github.chjiae.service.entity.ai.AiGatewaySnapshotOutbox;
import com.github.chjiae.service.entity.ai.AiGatewaySnapshotRevision;
import com.github.chjiae.service.mapper.ai.AiGatewaySnapshotOutboxMapper;
import com.github.chjiae.service.mapper.ai.AiGatewaySnapshotRevisionMapper;
import com.github.chjiae.service.service.ai.GatewaySnapshotOutboxProjector;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关快照控制面集成测试。
 * 覆盖控制面写操作与 revision/outbox 同事务记录、Projector 发布 Redis 快照、
 * 快照中不出现明文上游密钥，以及 Redis 清空后的重投影恢复。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewaySnapshotControlPlaneIntegrationTest extends BaseIntegrationTest {

    /** 本次测试运行的唯一后缀 */
    private static final String RUN_ID = Long.toString(System.currentTimeMillis());

    /** 测试明文密钥前缀，避免在断言失败消息中直接打印完整密钥 */
    private static final String SECRET_PREFIX = "phase04";

    /** 超级管理员令牌 */
    private static String adminToken;

    /** 租户所有者令牌 */
    private static String tenantOwnerToken;

    /** 租户 ID */
    private static Long tenantId;

    /** Provider ID */
    private static Long providerId;

    /** Connection ID */
    private static Long connectionId;

    /** Credential ID */
    private static Long credentialId;

    /** Resource ID */
    private static Long resourceId;

    /** 快照 revision Mapper */
    @Autowired
    private AiGatewaySnapshotRevisionMapper revisionMapper;

    /** 快照 outbox Mapper */
    @Autowired
    private AiGatewaySnapshotOutboxMapper outboxMapper;

    /** 快照投影器 */
    @Autowired
    private GatewaySnapshotOutboxProjector projector;

    /** Redis 字符串模板 */
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @Order(1)
    void setup_创建租户和AI运行配置() {
        adminToken = login("admin", "admin123");
        tenantOwnerToken = createTenantAndLoginOwner("snap_tenant_" + RUN_ID,
                "snap_owner_" + RUN_ID, "snap_owner_" + RUN_ID + "@test.com");

        JsonNode provider = assertSuccess(post("/api/v1/ai/providers", tenantOwnerToken,
                providerBody("openai_" + RUN_ID, "OpenAI Snapshot")));
        providerId = provider.get("id").asLong();
        tenantId = provider.get("tenantId").asLong();

        JsonNode connection = assertSuccess(post("/api/v1/ai/providers/" + providerId + "/connections",
                tenantOwnerToken, connectionBody("conn_" + RUN_ID, "https://api.snapshot.example/v1")));
        connectionId = connection.get("id").asLong();

        JsonNode model = assertSuccess(post("/api/v1/ai/models", tenantOwnerToken,
                modelBody("gpt_public_" + RUN_ID)));
        assertThat(model.get("id").asLong()).isPositive();

        JsonNode credential = assertSuccess(post("/api/v1/ai/providers/" + providerId + "/credentials",
                tenantOwnerToken, credentialBody("cred_" + RUN_ID, upstreamSecret())));
        credentialId = credential.get("id").asLong();

        JsonNode resource = assertSuccess(post("/api/v1/ai/resources", tenantOwnerToken,
                resourceBody(connectionId, credentialId, "res_" + RUN_ID)));
        resourceId = resource.get("id").asLong();
    }

    @Test
    @Order(2)
    void mutation_同一事务递增Revision并写入Outbox() {
        AiGatewaySnapshotRevision revision = revisionMapper.selectById(tenantId);
        assertThat(revision).isNotNull();
        assertThat(revision.getCurrentRevision()).isGreaterThanOrEqualTo(5L);

        List<AiGatewaySnapshotOutbox> outboxes = outboxMapper.selectList(
                new LambdaQueryWrapper<AiGatewaySnapshotOutbox>()
                        .eq(AiGatewaySnapshotOutbox::getTenantId, tenantId)
                        .orderByAsc(AiGatewaySnapshotOutbox::getRevision));

        assertThat(outboxes)
                .extracting(AiGatewaySnapshotOutbox::getChangeType)
                .contains("AI_PROVIDER_CHANGED",
                        "AI_CONNECTION_CHANGED",
                        "AI_PUBLIC_MODEL_CHANGED",
                        "AI_CREDENTIAL_CHANGED",
                        "AI_EXECUTION_RESOURCE_CHANGED");
        assertThat(outboxes)
                .extracting(AiGatewaySnapshotOutbox::getRevision)
                .doesNotHaveDuplicates();
    }

    @Test
    @Order(3)
    void projector_发布Redis快照且不包含明文密钥() {
        GatewaySnapshotOutboxProjector.ProjectorResult result = projector.projectPendingOnce("phase04-test");
        assertThat(result.publishedTenantCount()).isGreaterThanOrEqualTo(1);

        String currentKey = GatewaySnapshotRedisKeys.currentManifestKey(tenantId.toString());
        String manifestJson = redisTemplate.opsForValue().get(currentKey);
        assertThat(manifestJson).isNotBlank();
        assertThat(containsSecret(manifestJson)).isFalse();

        GatewaySnapshotManifest manifest = GatewaySnapshotJson.fromJson(manifestJson, GatewaySnapshotManifest.class);
        String payloadJson = redisTemplate.opsForValue().get(manifest.payloadRedisKey());
        assertThat(payloadJson).isNotBlank();
        assertThat(containsSecret(payloadJson)).isFalse();

        GatewayTenantSnapshot snapshot = GatewaySnapshotJson.fromJson(payloadJson, GatewayTenantSnapshot.class);
        assertThat(snapshot.tenantId()).isEqualTo(tenantId.toString());
        assertThat(snapshot.publicModels()).hasSize(1);
        assertThat(snapshot.executionResources()).hasSize(1);
        assertThat(snapshot.executionResources().getFirst().resourceId()).isEqualTo(resourceId.toString());
        assertThat(snapshot.executionResources().getFirst().secretEnvelope().ciphertextBase64()).isNotBlank();

        List<AiGatewaySnapshotOutbox> outboxes = outboxMapper.selectList(
                new LambdaQueryWrapper<AiGatewaySnapshotOutbox>()
                        .eq(AiGatewaySnapshotOutbox::getTenantId, tenantId));
        assertThat(outboxes).allMatch(outbox -> "PUBLISHED".equals(outbox.getStatus()));
    }

    @Test
    @Order(4)
    void projector_Redis清空后可通过重投影恢复() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        assertThat(redisTemplate.opsForSet().members(GatewaySnapshotRedisKeys.tenantIndexKey())).isEmpty();

        GatewaySnapshotOutboxProjector.ProjectorResult result = projector.reprojectAllTenantsOnce("phase04-rebuild");
        assertThat(result.publishedTenantCount()).isGreaterThanOrEqualTo(1);

        String manifestJson = redisTemplate.opsForValue()
                .get(GatewaySnapshotRedisKeys.currentManifestKey(tenantId.toString()));
        assertThat(manifestJson).isNotBlank();
        assertThat(containsSecret(manifestJson)).isFalse();
    }

    private String createTenantAndLoginOwner(String tenantCode, String adminUsername, String adminEmail) {
        String body = """
                {
                  "code": "%s",
                  "name": "%s",
                  "description": "快照测试租户",
                  "adminUsername": "%s",
                  "adminEmail": "%s",
                  "adminPassword": "test123456"
                }
                """.formatted(tenantCode, tenantCode, adminUsername, adminEmail);
        assertSuccess(post("/api/v1/tenants", adminToken, body));
        return login(adminUsername, "test123456");
    }

    private String providerBody(String code, String displayName) {
        return """
                {
                  "code": "%s",
                  "displayName": "%s",
                  "providerKind": "OPENAI",
                  "status": "ENABLED",
                  "description": "快照测试供应商"
                }
                """.formatted(code, displayName);
    }

    private String connectionBody(String code, String baseUrl) {
        return """
                {
                  "code": "%s",
                  "displayName": "快照测试连接",
                  "protocolType": "OPENAI_COMPATIBLE",
                  "baseUrl": "%s",
                  "status": "ENABLED",
                  "description": "快照测试连接"
                }
                """.formatted(code, baseUrl);
    }

    private String modelBody(String code) {
        return """
                {
                  "code": "%s",
                  "displayName": "公开模型",
                  "modelFamily": "gpt",
                  "status": "ENABLED",
                  "description": "快照测试模型"
                }
                """.formatted(code);
    }

    private String credentialBody(String code, String apiKey) {
        return """
                {
                  "code": "%s",
                  "displayName": "快照测试凭据",
                  "description": "快照测试凭据",
                  "apiKey": "%s"
                }
                """.formatted(code, apiKey);
    }

    private String resourceBody(Long connectionId, Long credentialId, String code) {
        return """
                {
                  "upstreamConnectionId": %d,
                  "credentialId": %d,
                  "code": "%s",
                  "displayName": "快照测试资源",
                  "description": "快照测试资源",
                  "adminStatus": "ENABLED"
                }
                """.formatted(connectionId, credentialId, code);
    }

    private String upstreamSecret() {
        return SECRET_PREFIX + "-" + RUN_ID + "-secret-value";
    }

    private boolean containsSecret(String text) {
        return text != null && text.contains(upstreamSecret());
    }
}
