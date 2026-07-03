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
 * 认证接口集成测试，覆盖登录、注册、令牌刷新、登出等场景。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthIntegrationTest extends BaseIntegrationTest {

    /** 超管令牌，供后续测试使用 */
    static String adminToken;

    /** 超管刷新令牌 */
    static String adminRefreshToken;

    @Test
    @Order(1)
    void login_正确凭据_返回令牌() {
        ResponseEntity<String> response = postPublic("/api/v1/auth/login",
                "{\"username\":\"admin\",\"password\":\"admin123\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);

        assertThat(data.get("accessToken").asText()).isNotBlank();
        assertThat(data.get("refreshToken").asText()).isNotBlank();

        JsonNode userInfo = data.get("userInfo");
        assertThat(userInfo.get("username").asText()).isEqualTo("admin");
        assertThat(userInfo.get("userType").asText()).isEqualTo("SUPER_ADMIN");
        assertThat(userInfo.get("roles").isArray()).isTrue();
        assertThat(userInfo.get("roles").get(0).asText()).isEqualTo("SUPER_ADMIN");

        adminToken = data.get("accessToken").asText();
        adminRefreshToken = data.get("refreshToken").asText();
    }

    @Test
    @Order(2)
    void login_错误密码_返回401() {
        ResponseEntity<String> response = postPublic("/api/v1/auth/login",
                "{\"username\":\"admin\",\"password\":\"wrongpassword\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(response, 401);
    }

    @Test
    @Order(3)
    void login_不存在的用户_返回401() {
        ResponseEntity<String> response = postPublic("/api/v1/auth/login",
                "{\"username\":\"nonexistent\",\"password\":\"admin123\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(response, 401);
    }

    @Test
    @Order(4)
    void login_空用户名_参数校验失败() {
        ResponseEntity<String> response = postPublic("/api/v1/auth/login",
                "{\"username\":\"\",\"password\":\"admin123\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(5)
    void refresh_有效刷新令牌_返回新令牌() {
        assertThat(adminRefreshToken).as("前置测试应已获取刷新令牌").isNotNull();

        ResponseEntity<String> response = postPublic("/api/v1/auth/refresh",
                "{\"refreshToken\":\"" + adminRefreshToken + "\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("accessToken").asText()).isNotBlank();
    }

    @Test
    @Order(6)
    void refresh_无效令牌_返回401() {
        ResponseEntity<String> response = postPublic("/api/v1/auth/refresh",
                "{\"refreshToken\":\"invalid.token.here\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(response, 401);
    }

    @Test
    @Order(7)
    void logout_返回成功() {
        ResponseEntity<String> response = postPublic("/api/v1/auth/logout", "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSuccess(response);
    }

    @Test
    @Order(8)
    void register_不存在的租户_返回404() {
        ResponseEntity<String> response = postPublic("/api/v1/auth/register",
                "{\"username\":\"newuser\",\"email\":\"new@test.com\",\"password\":\"test123456\",\"tenantCode\":\"NONEXISTENT\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(response, 404);
    }
}
