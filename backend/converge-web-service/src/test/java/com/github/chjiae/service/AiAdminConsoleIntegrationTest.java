package com.github.chjiae.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI Gateway 管理控制台支撑接口集成测试。
 * 覆盖枚举 options、Gateway 安全状态代理和非流式 Chat Completions 测试代理。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiAdminConsoleIntegrationTest extends BaseIntegrationTest {

    /** 本轮测试运行后缀，避免租户和用户编码重复。 */
    private static final String RUN_ID = Long.toString(System.currentTimeMillis());

    /** 模拟 Gateway 内部管理接口的本地 HTTP 服务。 */
    private static HttpServer mockGateway;

    /** 租户所有者访问令牌。 */
    private static String tenantOwnerToken;

    /** 租户成员访问令牌。 */
    private static String tenantMemberToken;

    /**
     * 启动本地模拟 Gateway，并注册固定安全响应。
     */
    @BeforeAll
    static void startMockGateway() throws IOException {
        mockGateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockGateway.createContext("/internal/ready", exchange -> writeJson(exchange, 200, """
                {
                  "status": "READY",
                  "service": "converge-gateway",
                  "loadedTenantCount": 1,
                  "payloadRedisKey": "forbidden",
                  "baseUrl": "forbidden"
                }
                """));
        mockGateway.createContext("/internal/snapshot-status", exchange -> writeJson(exchange, 200, """
                {
                  "status": "READY",
                  "loadedTenantCount": 1,
                  "loadedClientKeyCount": 2,
                  "loadedRoutePlanCount": 3,
                  "loadedRuntimePolicyCount": 4,
                  "snapshotRefreshFailedCount": 0,
                  "tenantRefreshPendingCount": 0,
                  "lastFullReconcileEpochMillis": 1783470000000,
                  "latestErrorCategory": null,
                  "redisKey": "forbidden",
                  "resourceId": "forbidden"
                }
                """));
        mockGateway.createContext("/internal/runtime-status", exchange -> writeJson(exchange, 200, """
                {
                  "state": "READY",
                  "redisAvailable": true,
                  "activeLocalLeases": 0,
                  "leaseAcquireGrantedCount": 5,
                  "runtimeStateUnavailableCount": 0,
                  "leaseId": "forbidden"
                }
                """));
        mockGateway.createContext("/v1/chat/completions", AiAdminConsoleIntegrationTest::handleChat);
        mockGateway.setExecutor(Executors.newCachedThreadPool());
        mockGateway.start();
    }

    /**
     * 停止本地模拟 Gateway。
     */
    @AfterAll
    static void stopMockGateway() {
        if (mockGateway != null) {
            mockGateway.stop(0);
        }
    }

    /**
     * 将模拟 Gateway 地址注入控制面代理配置。
     */
    @DynamicPropertySource
    static void configureGatewayAdminProperties(DynamicPropertyRegistry registry) {
        BaseIntegrationTest.configureProperties(registry);
        registry.add("ai.gateway.admin.enabled", () -> "true");
        registry.add("ai.gateway.admin.base-url", () -> "http://127.0.0.1:" + mockGateway.getAddress().getPort());
        registry.add("ai.gateway.admin.connect-timeout-ms", () -> "1000");
        registry.add("ai.gateway.admin.read-timeout-ms", () -> "1000");
    }

    @Test
    @Order(1)
    void setup_创建租户和角色用户() {
        String adminToken = login("admin", "admin123");
        tenantOwnerToken = createTenantAndLoginOwner(adminToken,
                "ai_console_" + RUN_ID,
                "ai_console_owner_" + RUN_ID,
                "ai_console_owner_" + RUN_ID + "@test.com");
        tenantMemberToken = createMemberUser(tenantOwnerToken,
                "ai_console_member_" + RUN_ID,
                "ai_console_member_" + RUN_ID + "@test.com");
        assertThat(tenantOwnerToken).isNotBlank();
        assertThat(tenantMemberToken).isNotBlank();
    }

    @Test
    @Order(2)
    void options_租户管理员可读取枚举且成员被拒绝() {
        JsonNode options = assertSuccess(get("/api/v1/ai/options", tenantOwnerToken));
        assertThat(options.get("providerKinds").get(0).get("name").asText()).isEqualTo("OPENAI");
        assertThat(options.get("protocolTypes").get(0).get("label").asText()).contains("OpenAI");
        assertThat(options.get("clientApiKeyStatuses").toString()).contains("REVOKED");
        assertThat(options.toString()).doesNotContain("com.github");

        ResponseEntity<String> memberResponse = get("/api/v1/ai/options", tenantMemberToken);
        assertThat(memberResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(3)
    void gatewayStatus_通过控制面代理读取且过滤敏感字段() {
        JsonNode ready = assertSuccess(get("/api/v1/ai/gateway/ready", tenantOwnerToken));
        assertThat(ready.get("status").asText()).isEqualTo("READY");
        assertThat(ready.toString()).doesNotContain("payloadRedisKey", "baseUrl");

        JsonNode snapshot = assertSuccess(get("/api/v1/ai/gateway/snapshot-status", tenantOwnerToken));
        assertThat(snapshot.get("loadedClientKeyCount").asInt()).isEqualTo(2);
        assertThat(snapshot.toString()).doesNotContain("redisKey", "resourceId");

        JsonNode runtime = assertSuccess(get("/api/v1/ai/gateway/runtime-status", tenantOwnerToken));
        assertThat(runtime.get("redisAvailable").asBoolean()).isTrue();
        assertThat(runtime.toString()).doesNotContain("leaseId");
    }

    @Test
    @Order(4)
    void chatTest_非流式代理成功且不泄露Authorization() {
        String body = """
                {
                  "clientApiKey": "cvg_live_test_once",
                  "model": "public-chat",
                  "messages": [
                    {"role": "user", "content": "你好"}
                  ],
                  "temperature": 0.2,
                  "maxTokens": 64
                }
                """;
        JsonNode response = assertSuccess(post("/api/v1/ai/gateway/test-chat-completions", tenantOwnerToken, body));
        assertThat(response.get("status").asInt()).isEqualTo(200);
        assertThat(response.get("data").get("model").asText()).isEqualTo("public-chat");
        assertThat(response.toString()).doesNotContain("Authorization", "cvg_live_test_once");
    }

    /**
     * 模拟 OpenAI Chat Completions 非流式响应。
     */
    private static void handleChat(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (!"Bearer cvg_live_test_once".equals(authorization)) {
            writeJson(exchange, 401, """
                    {"error":{"code":"invalid_api_key","message":"invalid key"}}
                    """);
            return;
        }
        writeJson(exchange, 200, """
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "model": "public-chat",
                  "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": "你好"}, "finish_reason": "stop"}
                  ]
                }
                """);
    }

    /**
     * 写出 JSON 响应。
     */
    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * 创建租户并登录其初始所有者。
     */
    private String createTenantAndLoginOwner(String adminToken, String tenantCode, String username, String email) {
        String body = """
                {
                  "code": "%s",
                  "name": "%s",
                  "description": "AI 管理台测试租户",
                  "adminUsername": "%s",
                  "adminEmail": "%s",
                  "adminPassword": "test123456"
                }
                """.formatted(tenantCode, tenantCode, username, email);
        assertSuccess(post("/api/v1/tenants", adminToken, body));
        return login(username, "test123456");
    }

    /**
     * 创建普通租户成员。
     */
    private String createMemberUser(String ownerToken, String username, String email) {
        String userBody = """
                {
                  "username": "%s",
                  "email": "%s",
                  "password": "test123456"
                }
                """.formatted(username, email);
        assertSuccess(post("/api/v1/users", ownerToken, userBody));
        return login(username, "test123456");
    }
}
