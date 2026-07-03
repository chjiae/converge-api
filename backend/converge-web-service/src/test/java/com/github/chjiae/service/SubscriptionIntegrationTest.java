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
 * 订阅管理接口集成测试，覆盖创建、标记付款、查询订阅等场景。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubscriptionIntegrationTest extends BaseIntegrationTest {

    static String adminToken;
    static String tenantAdminToken;
    static Long tenantId;
    static Long subscriptionId;

    @Test
    @Order(1)
    void setup_创建测试租户并获取令牌() {
        adminToken = login("admin", "admin123");

        // 创建一个专用租户用于订阅测试
        String tenantBody = """
                {
                  "code": "sub_test_tenant",
                  "name": "订阅测试租户",
                  "description": "用于订阅管理测试",
                  "adminUsername": "sub_admin",
                  "adminEmail": "sub_admin@test.com",
                  "adminPassword": "test123456"
                }
                """;
        ResponseEntity<String> resp = post("/api/v1/tenants", adminToken, tenantBody);
        JsonNode data = assertSuccess(resp);
        tenantId = data.get("id").asLong();

        // 登录租户管理员
        tenantAdminToken = login("sub_admin", "test123456");
        assertThat(tenantAdminToken).isNotBlank();
    }

    @Test
    @Order(2)
    void createSubscription_超管创建_返回成功() {
        String body = """
                {
                  "tenantId": %d,
                  "planType": "YEARLY",
                  "amount": 9999.00,
                  "startDate": "2026-07-01",
                  "endDate": "2027-07-01",
                  "paymentMethod": "OFFLINE",
                  "remark": "年度订阅-集成测试"
                }
                """.formatted(tenantId);
        ResponseEntity<String> response = post("/api/v1/subscriptions", adminToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("planType").asText()).isEqualTo("YEARLY");
        assertThat(data.get("status").asText()).isEqualTo("PENDING");
        subscriptionId = data.get("id").asLong();
    }

    @Test
    @Order(3)
    void listSubscriptions_超管查询全部_返回分页() {
        ResponseEntity<String> response = get("/api/v1/subscriptions?page=1&size=10", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("list").isArray()).isTrue();
        assertThat(data.get("total").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(4)
    void markAsPaid_标记付款_状态变为ACTIVE() {
        ResponseEntity<String> response = put(
                "/api/v1/subscriptions/" + subscriptionId + "/pay", adminToken, "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    @Order(5)
    void listMySubscriptions_租户管理员查询_返回本租户订阅() {
        ResponseEntity<String> response = get("/api/v1/my-subscriptions?page=1&size=10", tenantAdminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("list").isArray()).isTrue();
        assertThat(data.get("total").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(6)
    void initiateRenewal_租户管理员发起续费() {
        String body = """
                {
                  "tenantId": %d,
                  "planType": "MONTHLY",
                  "amount": 999.00,
                  "startDate": "2027-07-01",
                  "endDate": "2027-08-01",
                  "paymentMethod": "ALIPAY",
                  "remark": "月度续费"
                }
                """.formatted(tenantId);
        ResponseEntity<String> response = post("/api/v1/my-subscriptions", tenantAdminToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("planType").asText()).isEqualTo("MONTHLY");
        assertThat(data.get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    @Order(7)
    void subscription_未认证_返回401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/v1/subscriptions", String.class
        );
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
