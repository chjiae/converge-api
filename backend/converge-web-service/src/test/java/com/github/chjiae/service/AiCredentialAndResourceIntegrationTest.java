package com.github.chjiae.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.service.service.ai.AiCredentialService;
import com.github.chjiae.service.service.ai.AiExecutionResourceService;
import com.github.chjiae.service.tenant.TenantContext;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 凭据与可执行资源集成测试。
 * 覆盖凭据加密存储、租户隔离、权限控制、指纹去重、轮换、
 * 资源四方一致性校验、绑定不可变性和状态管理。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiCredentialAndResourceIntegrationTest extends BaseIntegrationTest {

    /** 本次测试运行的唯一后缀，避免多次运行时编码冲突 */
    private static final String RUN_ID = Long.toString(System.currentTimeMillis());

    /** 测试用 API Key（仅用于创建凭据，不出现在任何响应或日志中） */
    private static final String TEST_API_KEY = "sk-test-" + RUN_ID + "-abcdefghijklmnopqrstuvwxyz1234567890";

    /** 轮换后的新 API Key */
    private static final String ROTATED_API_KEY = "sk-rotated-" + RUN_ID + "-newkey9876543210abcdefghij";

    /** 用于测试重复拒绝的第二个相同 Key */
    private static final String DUPLICATE_API_KEY = TEST_API_KEY;

    /** 用于测试不同租户可使用相同 Key */
    private static final String SAME_KEY_DIFFERENT_TENANT = TEST_API_KEY;

    /** 超级管理员令牌 */
    private static String adminToken;

    /** 租户 A 所有者令牌 */
    private static String tenantOwnerTokenA;

    /** 租户 B 所有者令牌 */
    private static String tenantOwnerTokenB;

    /** 租户 A 成员令牌 */
    private static String tenantMemberTokenA;

    /** 租户 A 的供应商 ID */
    private static Long providerAId;

    /** 租户 B 的供应商 ID */
    private static Long providerBId;

    /** 租户 A 的连接 ID */
    private static Long connectionAId;

    /** 租户 B 的连接 ID */
    private static Long connectionBId;

    /** 租户 A 的凭据 ID */
    private static Long credentialAId;

    /** 租户 B 的凭据 ID（使用与 A 相同的原始 Key） */
    private static Long credentialBId;

    /** 租户 A 的资源 ID */
    private static Long resourceAId;

    /** 凭据服务（用于验证无租户上下文时的 Service 层显式拒绝） */
    @Autowired
    private AiCredentialService aiCredentialService;

    /** 资源服务（用于验证无租户上下文时的 Service 层显式拒绝） */
    @Autowired
    private AiExecutionResourceService aiExecutionResourceService;

    @Test
    @Order(1)
    void setup_创建两个租户和供应商连接() {
        adminToken = login("admin", "admin123");

        // 创建租户 A
        tenantOwnerTokenA = createTenantAndLoginOwner("cred_tenant_a_" + RUN_ID,
                "cred_owner_a_" + RUN_ID, "cred_owner_a_" + RUN_ID + "@test.com");

        // 创建租户 B
        tenantOwnerTokenB = createTenantAndLoginOwner("cred_tenant_b_" + RUN_ID,
                "cred_owner_b_" + RUN_ID, "cred_owner_b_" + RUN_ID + "@test.com");

        // 创建租户 A 成员
        tenantMemberTokenA = createMemberUser(tenantOwnerTokenA,
                "cred_member_" + RUN_ID, "cred_member_" + RUN_ID + "@test.com");

        // 租户 A 创建供应商
        JsonNode providerA = assertSuccess(post("/api/v1/ai/providers", tenantOwnerTokenA,
                providerBody("openai_" + RUN_ID, "OpenAI A")));
        providerAId = providerA.get("id").asLong();

        // 租户 B 创建供应商
        JsonNode providerB = assertSuccess(post("/api/v1/ai/providers", tenantOwnerTokenB,
                providerBody("openai_" + RUN_ID, "OpenAI B")));
        providerBId = providerB.get("id").asLong();

        // 租户 A 创建连接
        JsonNode connA = assertSuccess(post("/api/v1/ai/providers/" + providerAId + "/connections",
                tenantOwnerTokenA, connectionBody("conn_" + RUN_ID, "https://api.openai.com/v1")));
        connectionAId = connA.get("id").asLong();

        // 租户 B 创建连接
        JsonNode connB = assertSuccess(post("/api/v1/ai/providers/" + providerBId + "/connections",
                tenantOwnerTokenB, connectionBody("conn_" + RUN_ID, "https://api.openai.com/v1")));
        connectionBId = connB.get("id").asLong();

        assertThat(providerAId).isPositive();
        assertThat(providerBId).isPositive();
        assertThat(connectionAId).isPositive();
        assertThat(connectionBId).isPositive();
    }

    @Test
    @Order(2)
    void credential_未认证和成员无管理权限() {
        // 未认证
        ResponseEntity<String> unauth = postPublic("/api/v1/ai/providers/" + providerAId + "/credentials",
                credentialBody("cred_" + RUN_ID, TEST_API_KEY));
        assertThat(unauth.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);

        // 成员无权限
        ResponseEntity<String> memberResp = post("/api/v1/ai/providers/" + providerAId + "/credentials",
                tenantMemberTokenA, credentialBody("cred_" + RUN_ID, TEST_API_KEY));
        assertThat(memberResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(3)
    void credential_创建并验证响应不含明文和密文() {
        JsonNode created = assertSuccess(post("/api/v1/ai/providers/" + providerAId + "/credentials",
                tenantOwnerTokenA, credentialBody("cred_" + RUN_ID, TEST_API_KEY)));
        credentialAId = created.get("id").asLong();

        // 验证响应字段
        assertThat(created.get("code").asText()).isEqualTo("cred_" + RUN_ID);
        assertThat(created.get("credentialType").asText()).isEqualTo("API_KEY");
        assertThat(created.get("adminStatus").asText()).isEqualTo("ENABLED");
        assertThat(created.get("secretVersion").asInt()).isEqualTo(1);
        assertThat(created.get("maskedPreview").asText()).startsWith("sk-t");
        assertThat(created.get("maskedPreview").asText()).endsWith("7890");
        assertThat(created.get("maskedPreview").asText()).contains("...");

        // 响应中不得包含明文 API Key 或密文字段
        String responseBody = created.toString();
        assertThat(responseBody).doesNotContain(TEST_API_KEY);
        assertThat(created.has("encryptedSecret")).isFalse();
        assertThat(created.has("nonce")).isFalse();
        assertThat(created.has("secretFingerprint")).isFalse();
        assertThat(created.has("secretReference")).isFalse();
        assertThat(created.has("apiKey")).isFalse();
    }

    @Test
    @Order(4)
    void credential_同租户同供应商重复Key被拒绝() {
        ResponseEntity<String> dupResp = post("/api/v1/ai/providers/" + providerAId + "/credentials",
                tenantOwnerTokenA, credentialBody("cred_dup_" + RUN_ID, DUPLICATE_API_KEY));
        assertThat(dupResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(dupResp, 400);
    }

    @Test
    @Order(5)
    void credential_不同租户可使用相同Key() {
        // 租户 B 使用与 A 相同的原始 API Key，因 tenantId 不同指纹不同
        JsonNode created = assertSuccess(post("/api/v1/ai/providers/" + providerBId + "/credentials",
                tenantOwnerTokenB, credentialBody("cred_" + RUN_ID, SAME_KEY_DIFFERENT_TENANT)));
        credentialBId = created.get("id").asLong();

        assertThat(credentialBId).isNotEqualTo(credentialAId);
        assertThat(created.get("maskedPreview").asText()).contains("...");
    }

    @Test
    @Order(6)
    void credential_查询列表和详情() {
        // 列表
        JsonNode list = assertSuccess(get("/api/v1/ai/providers/" + providerAId
                + "/credentials?page=1&size=10", tenantOwnerTokenA));
        assertThat(list.get("total").asLong()).isGreaterThanOrEqualTo(1);

        // 详情
        JsonNode detail = assertSuccess(get("/api/v1/ai/credentials/" + credentialAId, tenantOwnerTokenA));
        assertThat(detail.get("code").asText()).isEqualTo("cred_" + RUN_ID);
        assertThat(detail.get("maskedPreview").asText()).contains("...");

        // 响应中不含敏感字段
        String detailStr = detail.toString();
        assertThat(detailStr).doesNotContain(TEST_API_KEY);
    }

    @Test
    @Order(7)
    void credential_更新管理元数据() {
        JsonNode updated = assertSuccess(put("/api/v1/ai/credentials/" + credentialAId, tenantOwnerTokenA,
                """
                {
                  "code": "cred_updated_%s",
                  "displayName": "更新后的凭据",
                  "description": "更新描述"
                }
                """.formatted(RUN_ID)));
        assertThat(updated.get("code").asText()).isEqualTo("cred_updated_" + RUN_ID);
        assertThat(updated.get("displayName").asText()).isEqualTo("更新后的凭据");
    }

    @Test
    @Order(8)
    void credential_轮换保留ID更新密文和掩码() {
        JsonNode rotated = assertSuccess(post("/api/v1/ai/credentials/" + credentialAId + "/rotate",
                tenantOwnerTokenA, """
                {"newApiKey": "%s"}
                """.formatted(ROTATED_API_KEY)));

        // ID 不变
        assertThat(rotated.get("id").asLong()).isEqualTo(credentialAId);
        // 版本号递增
        assertThat(rotated.get("secretVersion").asInt()).isEqualTo(2);
        // 轮换时间已设置
        assertThat(rotated.has("rotatedAt")).isTrue();
        assertThat(rotated.get("rotatedAt").isNull()).isFalse();
        // 掩码更新为新 Key 的掩码
        assertThat(rotated.get("maskedPreview").asText()).startsWith("sk-r");
        // 响应中不含新明文 Key
        assertThat(rotated.toString()).doesNotContain(ROTATED_API_KEY);
    }

    @Test
    @Order(9)
    void credential_启停操作() {
        JsonNode disabled = assertSuccess(post("/api/v1/ai/credentials/" + credentialAId + "/disable",
                tenantOwnerTokenA, "{}"));
        assertThat(disabled.get("adminStatus").asText()).isEqualTo("DISABLED");

        JsonNode enabled = assertSuccess(post("/api/v1/ai/credentials/" + credentialAId + "/enable",
                tenantOwnerTokenA, "{}"));
        assertThat(enabled.get("adminStatus").asText()).isEqualTo("ENABLED");
    }

    @Test
    @Order(10)
    void credential_跨租户读写被拒绝() {
        // 租户 A 不能读租户 B 的凭据
        ResponseEntity<String> crossRead = get("/api/v1/ai/credentials/" + credentialBId, tenantOwnerTokenA);
        assertThat(crossRead.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(crossRead, 404);

        // 租户 A 不能更新租户 B 的凭据
        ResponseEntity<String> crossUpdate = put("/api/v1/ai/credentials/" + credentialBId, tenantOwnerTokenA,
                """
                {"code": "hacked", "displayName": "越权"}
                """);
        assertThat(crossUpdate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(crossUpdate, 404);
    }

    @Test
    @Order(11)
    void credential_不提供删除和明文读取接口() {
        // 不提供删除接口
        assertDeleteNotSupported(delete("/api/v1/ai/credentials/" + credentialAId, tenantOwnerTokenA));

        // 不存在 /plaintext 或 /secret 之类的明文读取路径
        ResponseEntity<String> plaintextResp = get("/api/v1/ai/credentials/" + credentialAId + "/plaintext",
                tenantOwnerTokenA);
        assertThat(plaintextResp.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(12)
    void resource_创建并校验四方一致性() {
        JsonNode created = assertSuccess(post("/api/v1/ai/resources", tenantOwnerTokenA,
                resourceBody(connectionAId, credentialAId, "res_" + RUN_ID)));
        resourceAId = created.get("id").asLong();

        assertThat(created.get("resourceType").asText()).isEqualTo("DIRECT_API");
        assertThat(created.get("providerId").asLong()).isEqualTo(providerAId);
        assertThat(created.get("upstreamConnectionId").asLong()).isEqualTo(connectionAId);
        assertThat(created.get("credentialId").asLong()).isEqualTo(credentialAId);
        assertThat(created.get("adminStatus").asText()).isEqualTo("ENABLED");
    }

    @Test
    @Order(13)
    void resource_跨租户连接和凭据被拒绝() {
        // 租户 A 的连接 + 租户 B 的凭据
        ResponseEntity<String> crossResp = post("/api/v1/ai/resources", tenantOwnerTokenA,
                resourceBody(connectionAId, credentialBId, "cross_res_" + RUN_ID));
        assertThat(crossResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(crossResp, 404);
    }

    @Test
    @Order(14)
    void resource_同租户不同供应商被拒绝() {
        // 在租户 A 创建第二个供应商
        JsonNode provider2 = assertSuccess(post("/api/v1/ai/providers", tenantOwnerTokenA,
                providerBody("anthropic_" + RUN_ID, "Anthropic A")));
        long provider2Id = provider2.get("id").asLong();

        // 在新供应商下创建连接和凭据
        JsonNode conn2 = assertSuccess(post("/api/v1/ai/providers/" + provider2Id + "/connections",
                tenantOwnerTokenA, connectionBody("conn2_" + RUN_ID, "https://api.anthropic.com/v1")));
        long conn2Id = conn2.get("id").asLong();

        String anthropicKey = "sk-ant-" + RUN_ID + "-sufficientlylongkeyfortesting1234567890";
        JsonNode cred2 = assertSuccess(post("/api/v1/ai/providers/" + provider2Id + "/credentials",
                tenantOwnerTokenA, credentialBody("cred2_" + RUN_ID, anthropicKey)));
        long cred2Id = cred2.get("id").asLong();

        // 跨供应商：供应商 A 的连接 + 供应商 Anthropic 的凭据
        ResponseEntity<String> crossProvider = post("/api/v1/ai/resources", tenantOwnerTokenA,
                resourceBody(connectionAId, cred2Id, "cross_provider_" + RUN_ID));
        assertThat(crossProvider.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(crossProvider, 400);
    }

    @Test
    @Order(15)
    void resource_禁用连接或凭据不允许创建资源() {
        // 停用连接
        assertSuccess(post("/api/v1/ai/connections/" + connectionAId + "/disable", tenantOwnerTokenA, "{}"));

        // 尝试创建资源（连接已停用）
        ResponseEntity<String> disabledConn = post("/api/v1/ai/resources", tenantOwnerTokenA,
                resourceBody(connectionAId, credentialAId, "res_disabled_conn_" + RUN_ID));
        assertThat(disabledConn.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(disabledConn, 400);

        // 恢复连接
        assertSuccess(post("/api/v1/ai/connections/" + connectionAId + "/enable", tenantOwnerTokenA, "{}"));

        // 停用凭据
        assertSuccess(post("/api/v1/ai/credentials/" + credentialAId + "/disable", tenantOwnerTokenA, "{}"));

        // 尝试创建资源（凭据已停用）
        ResponseEntity<String> disabledCred = post("/api/v1/ai/resources", tenantOwnerTokenA,
                resourceBody(connectionAId, credentialAId, "res_disabled_cred_" + RUN_ID));
        assertThat(disabledCred.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(disabledCred, 400);

        // 恢复凭据
        assertSuccess(post("/api/v1/ai/credentials/" + credentialAId + "/enable", tenantOwnerTokenA, "{}"));
    }

    @Test
    @Order(16)
    void resource_重复连接和凭据组合被拒绝() {
        ResponseEntity<String> dupResp = post("/api/v1/ai/resources", tenantOwnerTokenA,
                resourceBody(connectionAId, credentialAId, "res_dup_" + RUN_ID));
        assertThat(dupResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(dupResp, 400);
    }

    @Test
    @Order(17)
    void resource_更新仅允许元数据不允许变更绑定() {
        JsonNode updated = assertSuccess(put("/api/v1/ai/resources/" + resourceAId, tenantOwnerTokenA,
                """
                {
                  "code": "res_updated_%s",
                  "displayName": "更新后的资源",
                  "description": "更新描述"
                }
                """.formatted(RUN_ID)));
        assertThat(updated.get("code").asText()).isEqualTo("res_updated_" + RUN_ID);
        // 绑定不变
        assertThat(updated.get("upstreamConnectionId").asLong()).isEqualTo(connectionAId);
        assertThat(updated.get("credentialId").asLong()).isEqualTo(credentialAId);
        assertThat(updated.get("providerId").asLong()).isEqualTo(providerAId);
    }

    @Test
    @Order(18)
    void resource_状态管理启用停用排空() {
        JsonNode disabled = assertSuccess(post("/api/v1/ai/resources/" + resourceAId + "/disable",
                tenantOwnerTokenA, "{}"));
        assertThat(disabled.get("adminStatus").asText()).isEqualTo("DISABLED");

        JsonNode draining = assertSuccess(post("/api/v1/ai/resources/" + resourceAId + "/drain",
                tenantOwnerTokenA, "{}"));
        assertThat(draining.get("adminStatus").asText()).isEqualTo("DRAINING");

        JsonNode enabled = assertSuccess(post("/api/v1/ai/resources/" + resourceAId + "/enable",
                tenantOwnerTokenA, "{}"));
        assertThat(enabled.get("adminStatus").asText()).isEqualTo("ENABLED");
    }

    @Test
    @Order(19)
    void resource_不提供删除接口() {
        assertDeleteNotSupported(delete("/api/v1/ai/resources/" + resourceAId, tenantOwnerTokenA));
    }

    @Test
    @Order(20)
    void service_无租户上下文时明确拒绝() {
        TenantContext.clear();

        assertThatThrownBy(() -> aiCredentialService.listCredentials(providerAId, 1, 10, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户");

        assertThatThrownBy(() -> aiExecutionResourceService.listResources(1, 10, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户");
    }

    @Test
    @Order(21)
    void superAdmin_无租户上下文不能管理AI资源() {
        // 超管虽然有 adminToken，但无租户上下文时 SecurityConfig 的 @PreAuthorize 允许进入
        // 但 Service 层的 tenantGuard 会拒绝空 tenantId
        ResponseEntity<String> adminListCred = get("/api/v1/ai/providers/" + providerAId
                + "/credentials?page=1&size=10", adminToken);
        assertThat(adminListCred.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> adminListRes = get("/api/v1/ai/resources?page=1&size=10", adminToken);
        assertThat(adminListRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ========== 辅助方法 ==========

    private String createTenantAndLoginOwner(String tenantCode, String adminUsername, String adminEmail) {
        String body = """
                {
                  "code": "%s",
                  "name": "%s",
                  "description": "凭据测试租户",
                  "adminUsername": "%s",
                  "adminEmail": "%s",
                  "adminPassword": "test123456"
                }
                """.formatted(tenantCode, tenantCode, adminUsername, adminEmail);
        assertSuccess(post("/api/v1/tenants", adminToken, body));
        return login(adminUsername, "test123456");
    }

    private String createMemberUser(String ownerToken, String username, String email) {
        String userBody = """
                {
                  "username": "%s",
                  "email": "%s",
                  "password": "test123456"
                }
                """.formatted(username, email);
        JsonNode user = assertSuccess(post("/api/v1/users", ownerToken, userBody));
        long roleId = findRoleId(ownerToken, "TENANT_MEMBER");
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
        JsonNode root = parseJson(response.getBody());
        assertThat(root.get("code").asInt()).isNotEqualTo(200);
    }

    private String providerBody(String code, String displayName) {
        return """
                {
                  "code": "%s",
                  "displayName": "%s",
                  "providerKind": "OPENAI",
                  "status": "ENABLED",
                  "description": "测试供应商"
                }
                """.formatted(code, displayName);
    }

    private String connectionBody(String code, String baseUrl) {
        return """
                {
                  "code": "%s",
                  "displayName": "测试连接",
                  "protocolType": "OPENAI_COMPATIBLE",
                  "baseUrl": "%s",
                  "status": "ENABLED",
                  "description": "测试连接"
                }
                """.formatted(code, baseUrl);
    }

    private String credentialBody(String code, String apiKey) {
        return """
                {
                  "code": "%s",
                  "displayName": "测试凭据",
                  "description": "测试凭据描述",
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
                  "displayName": "测试资源",
                  "description": "测试资源描述",
                  "adminStatus": "ENABLED"
                }
                """.formatted(connectionId, credentialId, code);
    }
}
