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
    static Long monthlyPlanId;

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
    void subscriptionPlans_查询启用套餐_返回默认套餐() {
        ResponseEntity<String> response = get("/api/v1/subscription-plans/enabled", tenantAdminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThanOrEqualTo(3);

        for (JsonNode item : data) {
            if ("MONTHLY".equals(item.get("planType").asText())) {
                monthlyPlanId = item.get("id").asLong();
                assertThat(item.get("name").asText()).isNotBlank();
                assertThat(item.get("finalPrice").decimalValue()).isEqualByComparingTo("99.00");
            }
        }
        assertThat(monthlyPlanId).as("应存在默认月付套餐").isNotNull();
    }

    @Test
    @Order(8)
    void subscriptionPlans_超管设置折扣价_租户续费使用折扣价() {
        String updateBody = """
                {
                  "name": "月付套餐",
                  "planType": "MONTHLY",
                  "durationMonths": 1,
                  "originalPrice": 99.00,
                  "discountName": "节日特惠",
                  "discountPrice": 88.00,
                  "discountStartAt": "2026-01-01 00:00:00",
                  "discountEndAt": "2026-12-31 23:59:59",
                  "benefits": "适合短期体验\\n完整 API 权限",
                  "enabled": true,
                  "recommended": false,
                  "sortOrder": 1
                }
                """;
        ResponseEntity<String> updateResponse = put("/api/v1/subscription-plans/" + monthlyPlanId, adminToken, updateBody);
        assertSuccess(updateResponse);

        String renewalBody = """
                {
                  "planId": %d,
                  "paymentMethod": "OFFLINE",
                  "remark": "节日折扣续费"
                }
                """.formatted(monthlyPlanId);
        ResponseEntity<String> renewalResponse = post("/api/v1/my-subscriptions/renewals", tenantAdminToken, renewalBody);

        JsonNode renewalData = assertSuccess(renewalResponse);
        assertThat(renewalData.get("planType").asText()).isEqualTo("MONTHLY");
        assertThat(renewalData.get("amount").decimalValue()).isEqualByComparingTo("88.00");
        assertThat(renewalData.get("status").asText()).isEqualTo("PENDING");
        assertThat(renewalData.get("paymentMethod").asText()).isEqualTo("OFFLINE");
    }

    @Test
    @Order(9)
    void subscription_未认证_返回401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/v1/subscriptions", String.class
        );
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
