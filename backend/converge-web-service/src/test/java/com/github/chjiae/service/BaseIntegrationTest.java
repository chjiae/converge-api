package com.github.chjiae.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试基类，提供 Testcontainers 基础设施和通用辅助方法。
 * 使用 GenericContainer 手动管理 PostgreSQL 和 Redis 容器，
 * 通过 DynamicPropertySource 注入连接配置。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    /** PostgreSQL 容器 */
    static final GenericContainer<?> postgres;

    /** Redis 容器 */
    static final GenericContainer<?> redis;

    static {
        postgres = new GenericContainer<>(DockerImageName.parse("postgres:18.4-alpine"))
                .withExposedPorts(5432)
                .withEnv("POSTGRES_DB", "converge_api_test")
                .withEnv("POSTGRES_USER", "test")
                .withEnv("POSTGRES_PASSWORD", "test");
        postgres.start();

        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redis.start();
    }

    /**
     * 动态注入 PostgreSQL 和 Redis 连接配置，覆盖 application.yml 中的值
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/converge_api_test");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("app.register-verification.expose-captcha-answer", () -> "true");
    }

    @LocalServerPort
    protected int port;

    /** JSON 解析器，直接实例化避免 Spring Boot 4 模块化问题 */
    protected final ObjectMapper objectMapper = new ObjectMapper();

    /** RestTemplate 实例，禁用默认错误处理器以允许测试断言错误响应 */
    protected final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        // 替换默认的 StringHttpMessageConverter，使其支持 application/json
        rt.getMessageConverters().removeIf(c -> c instanceof org.springframework.http.converter.StringHttpMessageConverter);
        org.springframework.http.converter.StringHttpMessageConverter jsonStrConverter =
                new org.springframework.http.converter.StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8);
        jsonStrConverter.setSupportedMediaTypes(java.util.List.of(
                MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));
        rt.getMessageConverters().add(0, jsonStrConverter);
        // 禁用默认错误处理器，允许测试断言错误响应
        rt.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.HttpStatusCode statusCode) {
                return false; // 不抛出任何 HTTP 错误异常
            }
        });
        return rt;
    }

    /** 基地址 */
    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    // ========== 登录辅助方法 ==========

    /**
     * 使用指定用户名和密码登录，返回访问令牌
     *
     * @param username 用户名
     * @param password 密码
     * @return JWT 访问令牌
     */
    protected String login(String username, String password) {
        var body = java.util.Map.of("username", username, "password", password);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/auth/login",
                new HttpEntity<>(body, headers),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            assertThat(root.get("code").asInt()).isEqualTo(200);
            return root.get("data").get("accessToken").asText();
        } catch (Exception e) {
            throw new RuntimeException("登录响应解析失败", e);
        }
    }

    /**
     * 构建带 JWT 认证头的 HttpHeaders
     *
     * @param token JWT 令牌
     * @return 包含 Authorization 头的 HttpHeaders
     */
    protected HttpHeaders authHeaders(String token) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    // ========== 响应解析辅助方法 ==========

    /**
     * 解析 JSON 响应体为 JsonNode
     */
    protected JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("JSON 解析失败: " + body, e);
        }
    }

    /**
     * 断言响应 code 为 200（成功），返回 data 节点
     */
    protected JsonNode assertSuccess(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = parseJson(response.getBody());
        assertThat(root.get("code").asInt())
                .as("响应 code 应为 200，实际 message: %s", root.has("message") ? root.get("message").asText() : "无")
                .isEqualTo(200);
        return root.get("data");
    }

    /**
     * 断言响应 code 为指定错误码，返回错误消息
     */
    protected String assertError(ResponseEntity<String> response, int expectedCode) {
        JsonNode root = parseJson(response.getBody());
        assertThat(root.get("code").asInt()).isEqualTo(expectedCode);
        return root.has("message") ? root.get("message").asText() : "";
    }

    // ========== HTTP 请求辅助方法 ==========

    /** 发送 GET 请求（带认证） */
    protected ResponseEntity<String> get(String path, String token) {
        return restTemplate.exchange(
                baseUrl() + path, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class
        );
    }

    /** 发送 POST 请求（带认证和 JSON body） */
    protected ResponseEntity<String> post(String path, String token, Object body) {
        String json = body instanceof String ? (String) body : toJson(body);
        return restTemplate.exchange(
                baseUrl() + path, HttpMethod.POST,
                new HttpEntity<>(json, authHeaders(token)),
                String.class
        );
    }

    /** 发送 PUT 请求（带认证和 JSON body） */
    protected ResponseEntity<String> put(String path, String token, Object body) {
        String json = body instanceof String ? (String) body : toJson(body);
        return restTemplate.exchange(
                baseUrl() + path, HttpMethod.PUT,
                new HttpEntity<>(json, authHeaders(token)),
                String.class
        );
    }

    /** 发送 DELETE 请求（带认证） */
    protected ResponseEntity<String> delete(String path, String token) {
        return restTemplate.exchange(
                baseUrl() + path, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), String.class
        );
    }

    /** 发送不带认证的 POST 请求 */
    protected ResponseEntity<String> postPublic(String path, Object body) {
        String json = body instanceof String ? (String) body : toJson(body);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl() + path, HttpMethod.POST,
                new HttpEntity<>(json, headers),
                String.class
        );
    }

    /** 将对象序列化为 JSON 字符串 */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }
}
