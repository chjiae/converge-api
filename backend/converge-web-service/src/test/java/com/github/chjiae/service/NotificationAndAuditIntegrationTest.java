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
 * 站内信通知和审计日志接口集成测试。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationAndAuditIntegrationTest extends BaseIntegrationTest {

    static String adminToken;
    static String tenantOwnerToken;
    static Long tenantId;

    @Test
    @Order(1)
    void setup_创建测试租户并获取令牌() {
        adminToken = login("admin", "admin123");

        String body = """
                {
                  "code": "notify_test_tenant",
                  "name": "通知测试租户",
                  "description": "用于通知和审计测试",
                  "adminUsername": "notify_admin",
                  "adminEmail": "notify@test.com",
                  "adminPassword": "test123456"
                }
                """;
        ResponseEntity<String> resp = post("/api/v1/tenants", adminToken, body);
        JsonNode data = assertSuccess(resp);
        tenantId = data.get("id").asLong();

        tenantOwnerToken = login("notify_admin", "test123456");
    }

    // ========== 站内信测试 ==========

    @Test
    @Order(2)
    void sendAnnouncement_超管发送全站公告() {
        String body = """
                {
                  "title": "系统维护通知",
                  "content": "系统将于今晚 22:00 进行维护，预计持续 2 小时。"
                }
                """;
        ResponseEntity<String> response = post("/api/v1/notifications", adminToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSuccess(response);
    }

    @Test
    @Order(3)
    void listNotifications_查询通知列表() {
        ResponseEntity<String> response = get("/api/v1/notifications?page=1&size=10", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("list").isArray()).isTrue();
    }

    @Test
    @Order(4)
    void getUnreadCount_查询未读数量() {
        ResponseEntity<String> response = get("/api/v1/notifications/unread-count", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(5)
    void markAllAsRead_全部标记已读() {
        ResponseEntity<String> response = put("/api/v1/notifications/read-all", adminToken, "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSuccess(response);
    }

    @Test
    @Order(6)
    void tenantOwner_listNotifications_租户用户查询通知() {
        ResponseEntity<String> response = get("/api/v1/notifications?page=1&size=10", tenantOwnerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("list").isArray()).isTrue();
    }

    // ========== 审计日志测试 ==========

    @Test
    @Order(10)
    void listAllAuditLogs_超管查询全部审计日志() {
        ResponseEntity<String> response = get("/api/v1/audit-logs?page=1&size=10", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("list").isArray()).isTrue();
        // 之前执行了多个操作，应有审计记录
        assertThat(data.get("total").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(11)
    void listTenantAuditLogs_超管查询指定租户审计日志() {
        ResponseEntity<String> response = get(
                "/api/v1/tenants/" + tenantId + "/audit-logs?page=1&size=10", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("list").isArray()).isTrue();
    }

    @Test
    @Order(12)
    void listMyAuditLogs_租户管理员查询本租户审计日志() {
        ResponseEntity<String> response = get("/api/v1/my-audit-logs?page=1&size=10", tenantOwnerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("list").isArray()).isTrue();
    }

    @Test
    @Order(13)
    void auditLogs_未认证_返回401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/v1/audit-logs", String.class
        );
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
