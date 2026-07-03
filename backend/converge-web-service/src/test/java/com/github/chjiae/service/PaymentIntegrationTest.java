package com.github.chjiae.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付流程集成测试，覆盖在线支付失败（无网关配置）、线下支付标记和登录 Cookie 验证场景。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentIntegrationTest extends BaseIntegrationTest {

    /** 超管令牌 */
    static String adminToken;

    /** 租户管理员令牌 */
    static String tenantAdminToken;

    /** 测试租户 ID */
    static Long tenantId;

    /** PENDING 状态的订阅 ID（用于在线支付测试） */
    static Long pendingSubscriptionId;

    /** 另一个 PENDING 状态的订阅 ID（用于线下支付测试） */
    static Long offlineSubscriptionId;

    @Test
    @Order(1)
    void setup_创建测试租户和订阅() {
        adminToken = login("admin", "admin123");

        // 创建专用测试租户
        String tenantBody = """
                {
                  "code": "pay_test_tenant",
                  "name": "支付测试租户",
                  "description": "用于支付流程集成测试",
                  "adminUsername": "pay_admin",
                  "adminEmail": "pay_admin@test.com",
                  "adminPassword": "test123456"
                }
                """;
        ResponseEntity<String> resp = post("/api/v1/tenants", adminToken, tenantBody);
        JsonNode data = assertSuccess(resp);
        tenantId = data.get("id").asLong();

        // 登录租户管理员
        tenantAdminToken = login("pay_admin", "test123456");
        assertThat(tenantAdminToken).isNotBlank();

        // 创建第一个 PENDING 订阅（用于在线支付测试）
        String subBody1 = """
                {
                  "tenantId": %d,
                  "planType": "MONTHLY",
                  "amount": 99.00,
                  "startDate": "2026-07-01",
                  "endDate": "2026-08-01",
                  "paymentMethod": "OFFLINE",
                  "remark": "支付测试-在线支付"
                }
                """.formatted(tenantId);
        ResponseEntity<String> subResp1 = post("/api/v1/subscriptions", adminToken, subBody1);
        JsonNode subData1 = assertSuccess(subResp1);
        assertThat(subData1.get("status").asText()).isEqualTo("PENDING");
        pendingSubscriptionId = subData1.get("id").asLong();

        // 创建第二个 PENDING 订阅（用于线下支付测试）
        String subBody2 = """
                {
                  "tenantId": %d,
                  "planType": "YEARLY",
                  "amount": 999.00,
                  "startDate": "2026-07-01",
                  "endDate": "2027-07-01",
                  "paymentMethod": "OFFLINE",
                  "remark": "支付测试-线下支付"
                }
                """.formatted(tenantId);
        ResponseEntity<String> subResp2 = post("/api/v1/subscriptions", adminToken, subBody2);
        JsonNode subData2 = assertSuccess(subResp2);
        assertThat(subData2.get("status").asText()).isEqualTo("PENDING");
        offlineSubscriptionId = subData2.get("id").asLong();
    }

    @Test
    @Order(2)
    void initiatePayment_无支付网关_应返回错误() {
        // 以租户管理员身份发起在线支付，由于未配置支付网关应返回错误
        String payBody = """
                {
                  "subscriptionId": %d,
                  "paymentMethod": "ALIPAY"
                }
                """.formatted(pendingSubscriptionId);
        ResponseEntity<String> response = post("/api/v1/my-subscriptions/pay", tenantAdminToken, payBody);

        // 支付网关未配置，应返回业务错误
        assertError(response, 400);
    }

    @Test
    @Order(3)
    void markAsPaid_线下支付标记_状态变为ACTIVE() {
        // 超管标记订阅为已支付（线下支付模式）
        ResponseEntity<String> response = put(
                "/api/v1/subscriptions/" + offlineSubscriptionId + "/pay", adminToken, "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("status").asText())
                .as("线下支付标记后订阅状态应为 ACTIVE")
                .isEqualTo("ACTIVE");
        assertThat(data.get("id").asLong()).isEqualTo(offlineSubscriptionId);
    }

    @Test
    @Order(4)
    void login_应设置HttpOnly_Cookie() {
        // 直接发送登录请求并检查 Set-Cookie 响应头
        var body = java.util.Map.of("username", "admin", "password", "admin123");
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/auth/login",
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSuccess(response);

        // 验证 Set-Cookie 头中包含 access_token 和 refresh_token
        List<String> setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).as("响应应包含 Set-Cookie 头").isNotNull();

        boolean hasAccessTokenCookie = false;
        boolean hasRefreshTokenCookie = false;

        for (String cookie : setCookieHeaders) {
            if (cookie.startsWith("access_token=")) {
                hasAccessTokenCookie = true;
                // 验证 HttpOnly 属性
                assertThat(cookie.toLowerCase()).as("access_token Cookie 应设置 HttpOnly").contains("httponly");
            }
            if (cookie.startsWith("refresh_token=")) {
                hasRefreshTokenCookie = true;
                // 验证 HttpOnly 属性
                assertThat(cookie.toLowerCase()).as("refresh_token Cookie 应设置 HttpOnly").contains("httponly");
            }
        }

        assertThat(hasAccessTokenCookie).as("应设置 access_token Cookie").isTrue();
        assertThat(hasRefreshTokenCookie).as("应设置 refresh_token Cookie").isTrue();
    }
}
