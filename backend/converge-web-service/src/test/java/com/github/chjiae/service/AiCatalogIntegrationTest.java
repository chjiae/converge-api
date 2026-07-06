package com.github.chjiae.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.service.service.ai.AiProviderService;
import com.github.chjiae.service.service.ai.AiPublicModelService;
import com.github.chjiae.service.service.ai.AiUpstreamConnectionService;
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
 * AI 控制面目录集成测试，覆盖供应商、连接、公开模型的租户隔离、权限和校验规则。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiCatalogIntegrationTest extends BaseIntegrationTest {

    /** 本次测试运行的唯一后缀，避免多次运行时编码冲突。 */
    private static final String RUN_ID = Long.toString(System.currentTimeMillis());

    /** 超级管理员令牌。 */
    private static String adminToken;

    /** 租户 A 所有者令牌。 */
    private static String tenantOwnerTokenA;

    /** 租户 B 所有者令牌。 */
    private static String tenantOwnerTokenB;

    /** 租户 A 管理员令牌。 */
    private static String tenantAdminTokenA;

    /** 租户 A 成员令牌。 */
    private static String tenantMemberTokenA;

    /** 租户 A 的 Provider ID。 */
    private static Long providerAId;

    /** 租户 B 的 Provider ID。 */
    private static Long providerBId;

    /** 租户 A 的 Connection ID。 */
    private static Long connectionAId;

    /** 租户 A 的公开模型 ID。 */
    private static Long modelAId;

    /** 租户 B 的公开模型 ID。 */
    private static Long modelBId;

    /** Provider 服务，用于验证无租户上下文时的 Service 层显式拒绝。 */
    @Autowired
    private AiProviderService aiProviderService;

    /** Connection 服务，用于验证无租户上下文时的 Service 层显式拒绝。 */
    @Autowired
    private AiUpstreamConnectionService aiUpstreamConnectionService;

    /** PublicModel 服务，用于验证无租户上下文时的 Service 层显式拒绝。 */
    @Autowired
    private AiPublicModelService aiPublicModelService;

    @Test
    @Order(1)
    void setup_创建两个租户和不同角色用户() {
        adminToken = login("admin", "admin123");
        tenantOwnerTokenA = createTenantAndLoginOwner("ai_catalog_a_" + RUN_ID,
                "ai_catalog_owner_a_" + RUN_ID, "ai_catalog_owner_a_" + RUN_ID + "@test.com");
        tenantOwnerTokenB = createTenantAndLoginOwner("ai_catalog_b_" + RUN_ID,
                "ai_catalog_owner_b_" + RUN_ID, "ai_catalog_owner_b_" + RUN_ID + "@test.com");

        tenantAdminTokenA = createUserAndAssignRole(tenantOwnerTokenA,
                "ai_catalog_admin_" + RUN_ID, "ai_catalog_admin_" + RUN_ID + "@test.com", "TENANT_ADMIN");
        tenantMemberTokenA = createUserAndAssignRole(tenantOwnerTokenA,
                "ai_catalog_member_" + RUN_ID, "ai_catalog_member_" + RUN_ID + "@test.com", "TENANT_MEMBER");

        assertThat(tenantOwnerTokenA).isNotBlank();
        assertThat(tenantOwnerTokenB).isNotBlank();
        assertThat(tenantAdminTokenA).isNotBlank();
        assertThat(tenantMemberTokenA).isNotBlank();
    }

    @Test
    @Order(2)
    void provider_未认证和成员无管理权限() {
        ResponseEntity<String> unauthenticated = postPublic("/api/v1/ai/providers",
                providerCreateBody("openai_" + RUN_ID, "OpenAI"));
        assertThat(unauthenticated.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);

        ResponseEntity<String> memberResponse = post("/api/v1/ai/providers", tenantMemberTokenA,
                providerCreateBody("openai_member_" + RUN_ID, "成员供应商"));
        assertThat(memberResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(3)
    void provider_租户所有者和管理员可管理且同租户编码唯一() {
        JsonNode created = assertSuccess(post("/api/v1/ai/providers", tenantOwnerTokenA,
                providerCreateBody("openai_" + RUN_ID, "OpenAI")));
        providerAId = created.get("id").asLong();
        assertThat(created.get("code").asText()).isEqualTo("openai_" + RUN_ID);
        assertThat(created.get("status").asText()).isEqualTo("ENABLED");

        ResponseEntity<String> duplicate = post("/api/v1/ai/providers", tenantOwnerTokenA,
                providerCreateBody("openai_" + RUN_ID, "重复供应商"));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(duplicate, 400);

        JsonNode adminCreated = assertSuccess(post("/api/v1/ai/providers", tenantAdminTokenA,
                providerCreateBody("anthropic_" + RUN_ID, "Anthropic")));
        assertThat(adminCreated.get("code").asText()).isEqualTo("anthropic_" + RUN_ID);

        JsonNode list = assertSuccess(get("/api/v1/ai/providers?page=1&size=10&keyword=openai", tenantOwnerTokenA));
        assertThat(list.get("total").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode detail = assertSuccess(get("/api/v1/ai/providers/" + providerAId, tenantOwnerTokenA));
        assertThat(detail.get("displayName").asText()).isEqualTo("OpenAI");

        JsonNode updated = assertSuccess(put("/api/v1/ai/providers/" + providerAId, tenantOwnerTokenA,
                providerUpdateBody("openai_" + RUN_ID, "OpenAI 更新")));
        assertThat(updated.get("displayName").asText()).isEqualTo("OpenAI 更新");

        JsonNode disabled = assertSuccess(post("/api/v1/ai/providers/" + providerAId + "/disable", tenantOwnerTokenA, "{}"));
        assertThat(disabled.get("status").asText()).isEqualTo("DISABLED");

        JsonNode enabled = assertSuccess(post("/api/v1/ai/providers/" + providerAId + "/enable", tenantOwnerTokenA, "{}"));
        assertThat(enabled.get("status").asText()).isEqualTo("ENABLED");
    }

    @Test
    @Order(4)
    void provider_不同租户可重复编码但不能跨租户读写() {
        JsonNode tenantBCreated = assertSuccess(post("/api/v1/ai/providers", tenantOwnerTokenB,
                providerCreateBody("openai_" + RUN_ID, "OpenAI 租户 B")));
        providerBId = tenantBCreated.get("id").asLong();

        ResponseEntity<String> tenantAReadB = get("/api/v1/ai/providers/" + providerBId, tenantOwnerTokenA);
        assertThat(tenantAReadB.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(tenantAReadB, 404);

        ResponseEntity<String> tenantAUpdateB = put("/api/v1/ai/providers/" + providerBId, tenantOwnerTokenA,
                providerUpdateBody("openai_" + RUN_ID, "越权更新"));
        assertThat(tenantAUpdateB.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(tenantAUpdateB, 404);

        ResponseEntity<String> superAdminList = get("/api/v1/ai/providers?page=1&size=10", adminToken);
        assertThat(superAdminList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(5)
    void connection_校验Provider归属和BaseUrl规则() {
        JsonNode created = assertSuccess(post("/api/v1/ai/providers/" + providerAId + "/connections", tenantOwnerTokenA,
                connectionCreateBody("primary_" + RUN_ID, "https://api.example.com/v1")));
        connectionAId = created.get("id").asLong();
        assertThat(created.get("baseUrl").asText()).isEqualTo("https://api.example.com/v1/");

        JsonNode list = assertSuccess(get("/api/v1/ai/providers/" + providerAId
                + "/connections?page=1&size=10&keyword=primary", tenantOwnerTokenA));
        assertThat(list.get("total").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode detail = assertSuccess(get("/api/v1/ai/connections/" + connectionAId, tenantOwnerTokenA));
        assertThat(detail.get("providerId").asLong()).isEqualTo(providerAId);

        JsonNode updated = assertSuccess(put("/api/v1/ai/connections/" + connectionAId, tenantOwnerTokenA,
                connectionUpdateBody(providerAId, "primary_" + RUN_ID, "https://api.example.com/openai")));
        assertThat(updated.get("baseUrl").asText()).isEqualTo("https://api.example.com/openai/");

        assertBusinessError(post("/api/v1/ai/providers/" + providerAId + "/connections", tenantOwnerTokenA,
                connectionCreateBody("relative_" + RUN_ID, "/v1")), 400);
        assertBusinessError(post("/api/v1/ai/providers/" + providerAId + "/connections", tenantOwnerTokenA,
                connectionCreateBody("userinfo_" + RUN_ID, "https://user:pass@example.com/v1")), 400);
        assertBusinessError(post("/api/v1/ai/providers/" + providerAId + "/connections", tenantOwnerTokenA,
                connectionCreateBody("query_" + RUN_ID, "https://api.example.com/v1?token=1")), 400);
        assertBusinessError(post("/api/v1/ai/providers/" + providerAId + "/connections", tenantOwnerTokenA,
                connectionCreateBody("fragment_" + RUN_ID, "https://api.example.com/v1#frag")), 400);

        ResponseEntity<String> crossTenantProvider = post("/api/v1/ai/providers/" + providerBId + "/connections",
                tenantOwnerTokenA, connectionCreateBody("cross_" + RUN_ID, "https://api.example.com/v1"));
        assertThat(crossTenantProvider.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(crossTenantProvider, 404);

        JsonNode disabled = assertSuccess(post("/api/v1/ai/connections/" + connectionAId + "/disable", tenantOwnerTokenA, "{}"));
        assertThat(disabled.get("status").asText()).isEqualTo("DISABLED");

        JsonNode enabled = assertSuccess(post("/api/v1/ai/connections/" + connectionAId + "/enable", tenantOwnerTokenA, "{}"));
        assertThat(enabled.get("status").asText()).isEqualTo("ENABLED");
    }

    @Test
    @Order(6)
    void publicModel_租户内管理和隔离() {
        JsonNode created = assertSuccess(post("/api/v1/ai/models", tenantOwnerTokenA,
                modelCreateBody("gpt_public_" + RUN_ID, "GPT 公开模型")));
        modelAId = created.get("id").asLong();
        assertThat(created.get("code").asText()).isEqualTo("gpt_public_" + RUN_ID);

        assertBusinessError(post("/api/v1/ai/models", tenantOwnerTokenA,
                modelCreateBody("gpt_public_" + RUN_ID, "重复公开模型")), 400);

        JsonNode tenantBCreated = assertSuccess(post("/api/v1/ai/models", tenantOwnerTokenB,
                modelCreateBody("gpt_public_" + RUN_ID, "GPT 租户 B")));
        modelBId = tenantBCreated.get("id").asLong();

        JsonNode list = assertSuccess(get("/api/v1/ai/models?page=1&size=10&keyword=gpt_public", tenantOwnerTokenA));
        assertThat(list.get("total").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode updated = assertSuccess(put("/api/v1/ai/models/" + modelAId, tenantOwnerTokenA,
                modelUpdateBody("gpt_public_" + RUN_ID, "GPT 公开模型更新")));
        assertThat(updated.get("displayName").asText()).isEqualTo("GPT 公开模型更新");

        ResponseEntity<String> tenantAReadB = get("/api/v1/ai/models/" + modelBId, tenantOwnerTokenA);
        assertThat(tenantAReadB.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(tenantAReadB, 404);

        JsonNode disabled = assertSuccess(post("/api/v1/ai/models/" + modelAId + "/disable", tenantOwnerTokenA, "{}"));
        assertThat(disabled.get("status").asText()).isEqualTo("DISABLED");

        JsonNode enabled = assertSuccess(post("/api/v1/ai/models/" + modelAId + "/enable", tenantOwnerTokenA, "{}"));
        assertThat(enabled.get("status").asText()).isEqualTo("ENABLED");
    }

    @Test
    @Order(7)
    void service_无租户上下文时明确拒绝() {
        TenantContext.clear();

        assertThatThrownBy(() -> aiProviderService.listProviders(1, 10, null, AiCatalogStatus.ENABLED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户");
        assertThatThrownBy(() -> aiUpstreamConnectionService.listConnections(providerAId, 1, 10, null, AiCatalogStatus.ENABLED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户");
        assertThatThrownBy(() -> aiPublicModelService.listModels(1, 10, null, AiCatalogStatus.ENABLED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户");
    }

    @Test
    @Order(8)
    void api_不提供删除接口() {
        assertDeleteNotSupported(delete("/api/v1/ai/providers/" + providerAId, tenantOwnerTokenA));
        assertDeleteNotSupported(delete("/api/v1/ai/connections/" + connectionAId, tenantOwnerTokenA));
        assertDeleteNotSupported(delete("/api/v1/ai/models/" + modelAId, tenantOwnerTokenA));
    }

    /**
     * 创建租户并登录其初始所有者。
     *
     * @param tenantCode    租户编码
     * @param adminUsername 初始管理员用户名
     * @param adminEmail    初始管理员邮箱
     * @return 初始所有者访问令牌
     */
    private String createTenantAndLoginOwner(String tenantCode, String adminUsername, String adminEmail) {
        String body = """
                {
                  "code": "%s",
                  "name": "%s",
                  "description": "AI 目录测试租户",
                  "adminUsername": "%s",
                  "adminEmail": "%s",
                  "adminPassword": "test123456"
                }
                """.formatted(tenantCode, tenantCode, adminUsername, adminEmail);
        assertSuccess(post("/api/v1/tenants", adminToken, body));
        return login(adminUsername, "test123456");
    }

    /**
     * 创建租户内用户并分配指定角色。
     *
     * @param ownerToken 租户所有者令牌
     * @param username   用户名
     * @param email      邮箱
     * @param roleCode   角色编码
     * @return 新用户访问令牌
     */
    private String createUserAndAssignRole(String ownerToken, String username, String email, String roleCode) {
        String userBody = """
                {
                  "username": "%s",
                  "email": "%s",
                  "password": "test123456"
                }
                """.formatted(username, email);
        JsonNode user = assertSuccess(post("/api/v1/users", ownerToken, userBody));
        long roleId = findRoleId(ownerToken, roleCode);
        assertSuccess(put("/api/v1/users/" + user.get("id").asLong() + "/roles", ownerToken,
                "{\"roleIds\":[" + roleId + "]}"));
        return login(username, "test123456");
    }

    /**
     * 查询当前租户内指定角色 ID。
     *
     * @param token    访问令牌
     * @param roleCode 角色编码
     * @return 角色 ID
     */
    private long findRoleId(String token, String roleCode) {
        JsonNode roles = assertSuccess(get("/api/v1/roles", token));
        for (JsonNode role : roles) {
            if (roleCode.equals(role.get("code").asText())) {
                return role.get("id").asLong();
            }
        }
        throw new AssertionError("未找到角色: " + roleCode);
    }

    /**
     * 断言业务错误响应。
     *
     * @param response 响应
     * @param code     期望业务错误码
     */
    private void assertBusinessError(ResponseEntity<String> response, int code) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(response, code);
    }

    /**
     * 断言 DELETE 请求没有被业务接口支持。
     * 当前全局异常处理会把不支持的方法统一包装为 500，测试只约束“不存在删除能力”。
     *
     * @param response 删除请求响应
     */
    private void assertDeleteNotSupported(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
        JsonNode root = parseJson(response.getBody());
        assertThat(root.get("code").asInt()).isNotEqualTo(200);
    }

    /**
     * 构建 Provider 创建请求。
     *
     * @param code        编码
     * @param displayName 展示名称
     * @return JSON 请求体
     */
    private String providerCreateBody(String code, String displayName) {
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

    /**
     * 构建 Provider 更新请求。
     *
     * @param code        编码
     * @param displayName 展示名称
     * @return JSON 请求体
     */
    private String providerUpdateBody(String code, String displayName) {
        return providerCreateBody(code, displayName);
    }

    /**
     * 构建 Connection 创建请求。
     *
     * @param code    编码
     * @param baseUrl Base URL
     * @return JSON 请求体
     */
    private String connectionCreateBody(String code, String baseUrl) {
        return """
                {
                  "code": "%s",
                  "displayName": "主连接",
                  "protocolType": "OPENAI_COMPATIBLE",
                  "baseUrl": "%s",
                  "status": "ENABLED",
                  "description": "测试连接"
                }
                """.formatted(code, baseUrl);
    }

    /**
     * 构建 Connection 更新请求。
     *
     * @param providerId Provider ID
     * @param code       编码
     * @param baseUrl    Base URL
     * @return JSON 请求体
     */
    private String connectionUpdateBody(Long providerId, String code, String baseUrl) {
        return """
                {
                  "providerId": %d,
                  "code": "%s",
                  "displayName": "主连接更新",
                  "protocolType": "OPENAI_COMPATIBLE",
                  "baseUrl": "%s",
                  "status": "ENABLED",
                  "description": "测试连接更新"
                }
                """.formatted(providerId, code, baseUrl);
    }

    /**
     * 构建公开模型创建请求。
     *
     * @param code        编码
     * @param displayName 展示名称
     * @return JSON 请求体
     */
    private String modelCreateBody(String code, String displayName) {
        return """
                {
                  "code": "%s",
                  "displayName": "%s",
                  "modelFamily": "gpt",
                  "status": "ENABLED",
                  "description": "测试公开模型"
                }
                """.formatted(code, displayName);
    }

    /**
     * 构建公开模型更新请求。
     *
     * @param code        编码
     * @param displayName 展示名称
     * @return JSON 请求体
     */
    private String modelUpdateBody(String code, String displayName) {
        return modelCreateBody(code, displayName);
    }
}
