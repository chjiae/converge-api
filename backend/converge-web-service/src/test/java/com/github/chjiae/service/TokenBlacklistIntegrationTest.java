package com.github.chjiae.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Token 黑名单集成测试，覆盖登出后 Token 失效、新 Token 正常使用等场景。
 * 验证 Token 黑名单机制能正确阻止已注销的 Token 继续访问受保护接口。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TokenBlacklistIntegrationTest extends BaseIntegrationTest {

    /** 第一次登录获取的令牌 */
    static String firstToken;

    /** 第二次登录获取的新令牌 */
    static String secondToken;

    @Test
    @Order(1)
    void login_获取令牌并验证可用() {
        // 登录获取令牌
        firstToken = login("admin", "admin123");
        assertThat(firstToken).isNotBlank();

        // 使用该令牌访问受保护接口，应成功
        ResponseEntity<String> response = get("/api/v1/users/me", firstToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("username").asText()).isEqualTo("admin");
    }

    @Test
    @Order(2)
    void logout_将令牌加入黑名单() {
        // 使用令牌调用登出接口，将 Token 加入黑名单
        ResponseEntity<String> response = post("/api/v1/auth/logout", firstToken, "{}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSuccess(response);
    }

    @Test
    @Order(3)
    void blacklistedToken_访问受保护接口_应返回401() {
        // 使用已注销的令牌再次访问受保护接口，应被拒绝
        ResponseEntity<String> response = get("/api/v1/users/me", firstToken);

        // Token 已在黑名单中，Security 应返回 401 或 403
        assertThat(response.getStatusCode())
                .as("已注销的 Token 不应能访问受保护接口")
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(4)
    void login_获取新令牌_应正常使用() {
        // 重新登录获取新令牌（不同 jti）
        secondToken = login("admin", "admin123");
        assertThat(secondToken).isNotBlank();
        assertThat(secondToken).as("新令牌应与旧令牌不同").isNotEqualTo(firstToken);

        // 使用新令牌访问受保护接口，应成功
        ResponseEntity<String> response = get("/api/v1/users/me", secondToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("username").asText()).isEqualTo("admin");
    }
}
