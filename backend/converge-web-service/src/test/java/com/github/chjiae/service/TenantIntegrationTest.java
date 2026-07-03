package com.github.chjiae.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户管理接口集成测试，覆盖 CRUD、启用/停用等操作。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TenantIntegrationTest extends BaseIntegrationTest {

    static String adminToken;
    static Long createdTenantId;

    @Test
    @Order(1)
    void setup_获取超管令牌() {
        adminToken = login("admin", "admin123");
        assertThat(adminToken).isNotBlank();
    }

    @Test
    @Order(2)
    void createTenant_超管操作_创建成功() {
        String body = """
                {
                  "code": "test_tenant_01",
                  "name": "测试租户一",
                  "description": "集成测试用租户",
                  "adminUsername": "tenant01_admin",
                  "adminEmail": "admin01@test.com",
                  "adminPassword": "test123456"
                }
                """;
        ResponseEntity<String> response = post("/api/v1/tenants", adminToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("code").asText()).isEqualTo("test_tenant_01");
        assertThat(data.get("name").asText()).isEqualTo("测试租户一");
        assertThat(data.get("status").asText()).isEqualTo("ACTIVE");
        createdTenantId = data.get("id").asLong();
    }

    @Test
    @Order(3)
    void createTenant_重复编码_返回错误() {
        String body = """
                {
                  "code": "test_tenant_01",
                  "name": "重复租户",
                  "adminUsername": "dup_admin",
                  "adminEmail": "dup@test.com",
                  "adminPassword": "test123456"
                }
                """;
        ResponseEntity<String> response = post("/api/v1/tenants", adminToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode root = parseJson(response.getBody());
        assertThat(root.get("code").asInt()).isNotEqualTo(200);
    }

    @Test
    @Order(4)
    void listTenants_超管操作_返回分页列表() {
        ResponseEntity<String> response = get("/api/v1/tenants?page=1&size=10", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("list").isArray()).isTrue();
        assertThat(data.get("total").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(5)
    void getTenant_存在的ID_返回详情() {
        ResponseEntity<String> response = get("/api/v1/tenants/" + createdTenantId, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("id").asLong()).isEqualTo(createdTenantId);
        assertThat(data.get("code").asText()).isEqualTo("test_tenant_01");
    }

    @Test
    @Order(6)
    void updateTenant_更新名称和描述() {
        String body = "{\"name\":\"测试租户一（已更新）\",\"description\":\"更新后的描述\"}";
        ResponseEntity<String> response = put("/api/v1/tenants/" + createdTenantId, adminToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("name").asText()).isEqualTo("测试租户一（已更新）");
    }

    @Test
    @Order(7)
    void disableTenant_停用成功() {
        ResponseEntity<String> response = post("/api/v1/tenants/" + createdTenantId + "/disable", adminToken, "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSuccess(response);

        // 验证状态已变更
        ResponseEntity<String> getResp = get("/api/v1/tenants/" + createdTenantId, adminToken);
        JsonNode data = assertSuccess(getResp);
        assertThat(data.get("status").asText()).isEqualTo("DISABLED");
    }

    @Test
    @Order(8)
    void enableTenant_重新启用成功() {
        ResponseEntity<String> response = post("/api/v1/tenants/" + createdTenantId + "/enable", adminToken, "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSuccess(response);

        // 验证状态已恢复
        ResponseEntity<String> getResp = get("/api/v1/tenants/" + createdTenantId, adminToken);
        JsonNode data = assertSuccess(getResp);
        assertThat(data.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    @Order(9)
    void deleteTenant_软删除成功() {
        ResponseEntity<String> response = delete("/api/v1/tenants/" + createdTenantId, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSuccess(response);

        // 已删除的租户查询应返回错误
        ResponseEntity<String> getResp = get("/api/v1/tenants/" + createdTenantId, adminToken);
        JsonNode root = parseJson(getResp.getBody());
        assertThat(root.get("code").asInt()).isNotEqualTo(200);
    }

    @Test
    @Order(10)
    void tenantEndpoints_未认证_返回401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/v1/tenants", String.class
        );
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
