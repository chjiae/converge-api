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
 * 用户管理接口集成测试，覆盖 CRUD、状态变更、角色分配等场景。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserManagementIntegrationTest extends BaseIntegrationTest {

    static String adminToken;
    static String tenantOwnerToken;
    static Long tenantId;
    static Long createdUserId;
    static Long tenantOwnerRoleId;

    @Test
    @Order(1)
    void setup_创建测试租户并获取令牌() {
        adminToken = login("admin", "admin123");

        // 创建专用租户
        String body = """
                {
                  "code": "user_mgmt_tenant",
                  "name": "用户管理测试租户",
                  "description": "用于用户管理测试",
                  "adminUsername": "user_mgmt_admin",
                  "adminEmail": "user_mgmt@test.com",
                  "adminPassword": "test123456"
                }
                """;
        ResponseEntity<String> resp = post("/api/v1/tenants", adminToken, body);
        JsonNode data = assertSuccess(resp);
        tenantId = data.get("id").asLong();

        tenantOwnerToken = login("user_mgmt_admin", "test123456");
        assertThat(tenantOwnerToken).isNotBlank();
    }

    @Test
    @Order(2)
    void getCurrentUser_获取当前用户信息() {
        ResponseEntity<String> response = get("/api/v1/users/me", tenantOwnerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("username").asText()).isEqualTo("user_mgmt_admin");
    }

    @Test
    @Order(3)
    void createUser_租户管理员创建用户() {
        String body = """
                {
                  "username": "test_member_01",
                  "email": "member01@test.com",
                  "password": "test123456",
                  "phone": "13900139001"
                }
                """;
        ResponseEntity<String> response = post("/api/v1/users", tenantOwnerToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("username").asText()).isEqualTo("test_member_01");
        assertThat(data.get("status").asText()).isEqualTo("ACTIVE");
        createdUserId = data.get("id").asLong();
    }

    @Test
    @Order(4)
    void createUser_重复用户名_返回错误() {
        String body = """
                {
                  "username": "test_member_01",
                  "email": "dup_email@test.com",
                  "password": "test123456"
                }
                """;
        ResponseEntity<String> response = post("/api/v1/users", tenantOwnerToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode root = parseJson(response.getBody());
        assertThat(root.get("code").asInt()).isNotEqualTo(200);
    }

    @Test
    @Order(5)
    void listUsers_查询租户内用户列表() {
        ResponseEntity<String> response = get("/api/v1/users?page=1&size=10", tenantOwnerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("list").isArray()).isTrue();
        // 至少有租户管理员 + 新创建的成员
        assertThat(data.get("total").asLong()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(6)
    void updateCurrentUser_更新手机号() {
        String body = "{\"phone\":\"13800001111\"}";
        ResponseEntity<String> response = put("/api/v1/users/me", tenantOwnerToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("phone").asText()).isEqualTo("13800001111");
    }

    @Test
    @Order(7)
    void changePassword_修改密码() {
        String body = "{\"oldPassword\":\"test123456\",\"newPassword\":\"newpass123\"}";
        ResponseEntity<String> response = put("/api/v1/users/me/password", tenantOwnerToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSuccess(response);

        // 用新密码登录验证
        String newToken = login("user_mgmt_admin", "newpass123");
        assertThat(newToken).isNotBlank();

        // 恢复原密码以便后续测试使用
        put("/api/v1/users/me/password", newToken,
                "{\"oldPassword\":\"newpass123\",\"newPassword\":\"test123456\"}");
    }

    @Test
    @Order(8)
    void updateUserStatus_停用用户() {
        String body = "{\"status\":\"DISABLED\"}";
        ResponseEntity<String> response = put(
                "/api/v1/users/" + createdUserId + "/status", tenantOwnerToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("status").asText()).isEqualTo("DISABLED");
    }

    @Test
    @Order(9)
    void updateUserStatus_重新启用用户() {
        String body = "{\"status\":\"ACTIVE\"}";
        ResponseEntity<String> response = put(
                "/api/v1/users/" + createdUserId + "/status", tenantOwnerToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    @Order(10)
    void assignRoles_为用户分配角色() {
        // 先查询可用角色列表
        ResponseEntity<String> rolesResp = get("/api/v1/roles", tenantOwnerToken);
        JsonNode rolesData = assertSuccess(rolesResp);
        assertThat(rolesData.isArray()).isTrue();

        // 取第一个角色分配给用户
        long firstRoleId = rolesData.get(0).get("id").asLong();
        String body = "{\"roleIds\":[" + firstRoleId + "]}";
        ResponseEntity<String> response = put(
                "/api/v1/users/" + createdUserId + "/roles", tenantOwnerToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("roles").isArray()).isTrue();
    }

    @Test
    @Order(11)
    void deleteUser_删除用户() {
        ResponseEntity<String> response = delete(
                "/api/v1/users/" + createdUserId, tenantOwnerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSuccess(response);
    }
}
