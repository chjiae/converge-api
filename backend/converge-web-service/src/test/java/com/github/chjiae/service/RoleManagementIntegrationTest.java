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
 * 角色管理接口集成测试，覆盖 CRUD、权限分配等场景。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RoleManagementIntegrationTest extends BaseIntegrationTest {

    static String adminToken;
    static String tenantOwnerToken;
    static Long tenantId;
    static Long createdRoleId;

    @Test
    @Order(1)
    void setup_创建测试租户并获取令牌() {
        adminToken = login("admin", "admin123");

        String body = """
                {
                  "code": "role_mgmt_tenant",
                  "name": "角色管理测试租户",
                  "description": "用于角色管理测试",
                  "adminUsername": "role_mgmt_admin",
                  "adminEmail": "role_mgmt@test.com",
                  "adminPassword": "test123456"
                }
                """;
        ResponseEntity<String> resp = post("/api/v1/tenants", adminToken, body);
        JsonNode data = assertSuccess(resp);
        tenantId = data.get("id").asLong();

        tenantOwnerToken = login("role_mgmt_admin", "test123456");
    }

    @Test
    @Order(2)
    void listRoles_查询角色列表_包含系统角色和租户角色() {
        ResponseEntity<String> response = get("/api/v1/roles", tenantOwnerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.isArray()).isTrue();
        // 至少有 3 个内置租户角色 + 系统角色
        assertThat(data.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @Order(3)
    void createRole_创建自定义角色() {
        String body = """
                {
                  "code": "CUSTOM_VIEWER",
                  "name": "自定义查看者",
                  "description": "仅有查看权限的自定义角色"
                }
                """;
        ResponseEntity<String> response = post("/api/v1/roles", tenantOwnerToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("code").asText()).isEqualTo("CUSTOM_VIEWER");
        assertThat(data.get("name").asText()).isEqualTo("自定义查看者");
        createdRoleId = data.get("id").asLong();
    }

    @Test
    @Order(4)
    void createRole_重复编码_返回错误() {
        String body = """
                {
                  "code": "CUSTOM_VIEWER",
                  "name": "重复角色"
                }
                """;
        ResponseEntity<String> response = post("/api/v1/roles", tenantOwnerToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode root = parseJson(response.getBody());
        assertThat(root.get("code").asInt()).isNotEqualTo(200);
    }

    @Test
    @Order(5)
    void updateRole_更新角色信息() {
        String body = """
                {
                  "code": "CUSTOM_VIEWER",
                  "name": "自定义查看者（已更新）",
                  "description": "更新后的描述"
                }
                """;
        ResponseEntity<String> response = put("/api/v1/roles/" + createdRoleId, tenantOwnerToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("name").asText()).isEqualTo("自定义查看者（已更新）");
    }

    @Test
    @Order(6)
    void assignPermissions_分配权限() {
        // 分配一个权限 ID（使用 ID 1，假设为有效的权限）
        String body = "{\"permissionIds\":[1, 2, 3]}";
        ResponseEntity<String> response = put(
                "/api/v1/roles/" + createdRoleId + "/permissions", tenantOwnerToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSuccess(response);
    }

    @Test
    @Order(7)
    void deleteRole_删除自定义角色() {
        ResponseEntity<String> response = delete("/api/v1/roles/" + createdRoleId, tenantOwnerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSuccess(response);
    }

    @Test
    @Order(8)
    void deleteRole_系统角色不可删除() {
        // 查询系统角色（tenantId 为 null 的角色）
        ResponseEntity<String> rolesResp = get("/api/v1/roles", tenantOwnerToken);
        JsonNode roles = assertSuccess(rolesResp);

        // 找一个系统角色尝试删除
        Long systemRoleId = null;
        for (JsonNode role : roles) {
            if (role.has("tenantId") && role.get("tenantId").isNull()) {
                systemRoleId = role.get("id").asLong();
                break;
            }
        }

        if (systemRoleId != null) {
            ResponseEntity<String> response = delete("/api/v1/roles/" + systemRoleId, tenantOwnerToken);
            JsonNode root = parseJson(response.getBody());
            // 系统角色删除应被拒绝
            assertThat(root.get("code").asInt()).isNotEqualTo(200);
        }
    }
}
