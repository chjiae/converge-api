package com.github.chjiae.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证接口集成测试，覆盖登录、注册、令牌刷新、登出等场景。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthIntegrationTest extends BaseIntegrationTest {

    /** JDBC 工具，用于准备注册相关测试数据 */
    @Autowired
    JdbcTemplate jdbcTemplate;

    /** Redis 工具，用于读取测试环境中的邮箱验证码 */
    @Autowired
    StringRedisTemplate redisTemplate;

    /** 超管令牌，供后续测试使用 */
    static String adminToken;

    /** 超管刷新令牌 */
    static String adminRefreshToken;

    @BeforeEach
    void setUpRegisterTenant() {
        jdbcTemplate.update("""
                INSERT INTO tenant (id, code, name, status)
                VALUES (1001, 'TEST_TENANT', '测试租户', 'ACTIVE')
                ON CONFLICT (id) DO NOTHING
                """);
        jdbcTemplate.update("""
                INSERT INTO role (id, tenant_id, code, name, description, is_system)
                VALUES (1001, 1001, 'TENANT_MEMBER', '租户成员', '租户普通成员', true)
                ON CONFLICT (id) DO NOTHING
                """);
    }

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
    void register_未验证邮箱_返回400() {
        ResponseEntity<String> response = postPublic("/api/v1/auth/register",
                "{\"username\":\"newuser\",\"email\":\"new@test.com\",\"password\":\"test123456\",\"tenantCode\":\"TEST_TENANT\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(response, 400);
    }

    @Test
    @Order(9)
    void registerEmailCodeSend_错误人机验证码_拒绝发送() {
        ResponseEntity<String> response = postPublic("/api/v1/auth/register/email-code/send",
                "{\"email\":\"captcha-wrong@test.com\",\"captchaId\":\"missing\",\"captchaAnswer\":\"0000\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(response, 400);
    }

    @Test
    @Order(10)
    void registerEmailCodeSend_六十秒内重复发送_返回429() {
        JsonNode captcha = requestCaptcha();
        ResponseEntity<String> firstResponse = postPublic("/api/v1/auth/register/email-code/send",
                """
                {"email":"cooldown@test.com","captchaId":"%s","captchaAnswer":"%s"}
                """.formatted(captcha.get("captchaId").asText(), captcha.get("answer").asText()));
        assertSuccess(firstResponse);

        JsonNode secondCaptcha = requestCaptcha();
        ResponseEntity<String> secondResponse = postPublic("/api/v1/auth/register/email-code/send",
                """
                {"email":"cooldown@test.com","captchaId":"%s","captchaAnswer":"%s"}
                """.formatted(secondCaptcha.get("captchaId").asText(), secondCaptcha.get("answer").asText()));

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertError(secondResponse, 429);
    }

    @Test
    @Order(11)
    void register_验证码验证成功后_创建账户() {
        String email = "verified-register@test.com";
        JsonNode captcha = requestCaptcha();
        ResponseEntity<String> sendResponse = postPublic("/api/v1/auth/register/email-code/send",
                """
                {"email":"%s","captchaId":"%s","captchaAnswer":"%s"}
                """.formatted(email, captcha.get("captchaId").asText(), captcha.get("answer").asText()));
        assertSuccess(sendResponse);

        String code = redisTemplate.opsForValue().get("auth:register:email-code:" + email);
        assertThat(code).isNotBlank();

        ResponseEntity<String> verifyResponse = postPublic("/api/v1/auth/register/email-code/verify",
                """
                {"email":"%s","code":"%s"}
                """.formatted(email, code));
        JsonNode verifyData = assertSuccess(verifyResponse);
        String verificationToken = verifyData.get("verificationToken").asText();

        ResponseEntity<String> registerResponse = postPublic("/api/v1/auth/register",
                """
                {"username":"verifieduser","email":"%s","password":"test123456","tenantCode":"TEST_TENANT","verificationToken":"%s"}
                """.formatted(email, verificationToken));
        JsonNode registerData = assertSuccess(registerResponse);

        assertThat(registerData.get("userInfo").get("username").asText()).isEqualTo("verifieduser");
        assertThat(registerData.get("userInfo").get("email").asText()).isEqualTo(email);
    }

    /**
     * 请求测试验证码。
     *
     * 测试环境会返回 answer 字段，便于集成测试稳定完成服务端人机校验。
     *
     * @return 验证码响应数据
     */
    private JsonNode requestCaptcha() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/v1/auth/captcha", String.class);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("imageBase64").asText()).startsWith("data:image/png;base64,");
        return data;
    }
}
