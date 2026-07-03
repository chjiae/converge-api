package com.github.chjiae.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 卡密管理集成测试，覆盖卡密生成、查询、兑换和重复兑换等场景。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CardKeyIntegrationTest extends BaseIntegrationTest {

    /** 超管令牌 */
    static String adminToken;

    /** 租户管理员令牌 */
    static String tenantAdminToken;

    /** 测试租户 ID */
    static Long tenantId;

    /** 生成的卡密编码列表 */
    static String[] cardKeyCodes;

    @Test
    @Order(1)
    void setup_登录超管并创建测试租户() {
        adminToken = login("admin", "admin123");

        // 创建卡密兑换测试专用租户
        String tenantBody = """
                {
                  "code": "ck_test_tenant",
                  "name": "卡密测试租户",
                  "description": "用于卡密兑换集成测试",
                  "adminUsername": "ck_admin",
                  "adminEmail": "ck_admin@test.com",
                  "adminPassword": "test123456"
                }
                """;
        ResponseEntity<String> resp = post("/api/v1/tenants", adminToken, tenantBody);
        JsonNode data = assertSuccess(resp);
        tenantId = data.get("id").asLong();

        // 登录租户管理员
        tenantAdminToken = login("ck_admin", "test123456");
        assertThat(tenantAdminToken).isNotBlank();
    }

    @Test
    @Order(2)
    void generateCardKeys_超管生成3张月卡_应返回唯一编码() {
        String body = """
                {
                  "planType": "MONTHLY",
                  "durationDays": 30,
                  "amount": 99.00,
                  "count": 3
                }
                """;
        ResponseEntity<String> response = post("/api/v1/card-keys/generate", adminToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);

        // 验证返回了 3 张卡密
        assertThat(data.isArray()).as("响应 data 应为数组").isTrue();
        assertThat(data.size()).as("应生成 3 张卡密").isEqualTo(3);

        // 验证每张卡密的属性并收集编码
        cardKeyCodes = new String[3];
        Set<String> uniqueCodes = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            JsonNode cardKey = data.get(i);
            String code = cardKey.get("code").asText();
            assertThat(code).as("卡密编码不应为空").isNotBlank();
            assertThat(cardKey.get("planType").asText()).isEqualTo("MONTHLY");
            assertThat(cardKey.get("durationDays").asInt()).isEqualTo(30);
            assertThat(cardKey.get("amount").asDouble()).isEqualTo(99.00);
            assertThat(cardKey.get("status").asText()).isEqualTo("UNUSED");
            cardKeyCodes[i] = code;
            uniqueCodes.add(code);
        }

        // 验证编码唯一性
        assertThat(uniqueCodes.size()).as("3 张卡密编码应互不相同").isEqualTo(3);
    }

    @Test
    @Order(3)
    void listCardKeys_超管查询_应包含已生成的卡密() {
        ResponseEntity<String> response = get("/api/v1/card-keys?page=1&size=10", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("list").isArray()).isTrue();
        assertThat(data.get("total").asLong())
                .as("卡密列表应至少包含 3 条记录")
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    @Order(4)
    void redeemCardKey_租户管理员兑换_订阅应被激活() {
        assertThat(cardKeyCodes).as("前置测试应已生成卡密编码").isNotNull();
        String code = cardKeyCodes[0];

        // 租户管理员兑换第一张卡密
        String body = """
                {
                  "code": "%s"
                }
                """.formatted(code);
        ResponseEntity<String> response = post("/api/v1/my-subscriptions/redeem", tenantAdminToken, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("code").asText()).isEqualTo(code);
        assertThat(data.get("status").asText())
                .as("兑换后卡密状态应为 REDEEMED")
                .isEqualTo("REDEEMED");

        // 验证租户订阅列表中出现新的 ACTIVE 订阅
        ResponseEntity<String> subResp = get("/api/v1/my-subscriptions?page=1&size=10", tenantAdminToken);
        JsonNode subData = assertSuccess(subResp);
        assertThat(subData.get("list").isArray()).isTrue();
        assertThat(subData.get("total").asLong())
                .as("租户应有至少一条订阅记录")
                .isGreaterThanOrEqualTo(1);

        // 验证存在一条由卡密兑换产生的 ACTIVE 订阅
        boolean hasActiveCardKeySubscription = false;
        for (JsonNode sub : subData.get("list")) {
            if ("ACTIVE".equals(sub.get("status").asText())
                    && "CARD_KEY".equals(sub.get("paymentMethod").asText())) {
                hasActiveCardKeySubscription = true;
                break;
            }
        }
        assertThat(hasActiveCardKeySubscription)
                .as("应存在卡密兑换产生的 ACTIVE 订阅")
                .isTrue();
    }

    @Test
    @Order(5)
    void redeemCardKey_重复兑换同一编码_应返回错误() {
        assertThat(cardKeyCodes).as("前置测试应已生成卡密编码").isNotNull();
        String code = cardKeyCodes[0];

        // 尝试再次兑换同一编码
        String body = """
                {
                  "code": "%s"
                }
                """.formatted(code);
        ResponseEntity<String> response = post("/api/v1/my-subscriptions/redeem", tenantAdminToken, body);

        // 卡密已被兑换，应返回错误
        assertError(response, 400);
    }

    @Test
    @Order(6)
    void redeemCardKey_不存在的编码_应返回404() {
        String body = """
                {
                  "code": "INVALID_CODE_12345"
                }
                """;
        ResponseEntity<String> response = post("/api/v1/my-subscriptions/redeem", tenantAdminToken, body);

        assertError(response, 404);
    }
}
