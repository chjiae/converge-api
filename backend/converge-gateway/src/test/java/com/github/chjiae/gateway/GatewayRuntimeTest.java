package com.github.chjiae.gateway;

import com.github.chjiae.gateway.config.GatewayConfig;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网关运行时集成测试，覆盖独立启动、内部接口、请求 ID、错误响应和优雅关闭。
 */
@ExtendWith(VertxExtension.class)
class GatewayRuntimeTest {

    /** 测试用 HTTP 客户端 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    /** 当前测试启动的网关运行时 */
    private GatewayRuntime runtime;

    /**
     * 每个测试启动一个随机端口网关，避免固定占用 8081。
     *
     * @param testContext Vert.x 异步测试上下文
     */
    @BeforeEach
    void setUp(VertxTestContext testContext) throws Throwable {
        GatewayConfig config = new GatewayConfig("127.0.0.1", 0, 500, "converge-gateway-test", "test-version");
        GatewayRuntime.start(config, true)
                .onSuccess(started -> {
                    runtime = started;
                    testContext.completeNow();
                })
                .onFailure(testContext::failNow);
        await(testContext);
    }

    /**
     * 每个测试结束后关闭网关，确保不会遗留事件循环线程。
     *
     * @param testContext Vert.x 异步测试上下文
     */
    @AfterEach
    void tearDown(VertxTestContext testContext) throws Throwable {
        if (runtime == null) {
            testContext.completeNow();
            await(testContext);
            return;
        }
        runtime.close()
                .onSuccess(ignored -> {
                    runtime = null;
                    testContext.completeNow();
                })
                .onFailure(testContext::failNow);
        await(testContext);
    }

    @Test
    void start_随机端口启动_返回实际监听端口() {
        assertTrue(runtime.actualPort() > 0);
    }

    @Test
    void health_返回存活状态和生成的RequestId() throws Exception {
        HttpResponse<String> response = get("/internal/health", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("X-Request-Id").isPresent());
        assertTrue(response.body().contains("\"status\":\"UP\""));
        assertTrue(response.body().contains("\"service\":\"converge-gateway-test\""));
    }

    @Test
    void ready_启动后立即返回就绪() throws Exception {
        HttpResponse<String> response = get("/internal/ready", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"READY\""));
    }

    @Test
    void version_返回服务版本和运行时信息() throws Exception {
        HttpResponse<String> response = get("/internal/version", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"serviceName\":\"converge-gateway-test\""));
        assertTrue(response.body().contains("\"buildVersion\":\"test-version\""));
        assertTrue(response.body().contains("\"javaVersion\""));
        assertTrue(response.body().contains("\"vertxVersion\""));
        assertTrue(response.body().contains("\"startedAt\""));
    }

    @Test
    void requestId_合法请求Id会被透传() throws Exception {
        HttpResponse<String> response = get("/internal/health", "verify-20260706");

        assertEquals(200, response.statusCode());
        assertEquals("verify-20260706", response.headers().firstValue("X-Request-Id").orElse(""));
        assertTrue(response.body().contains("\"requestId\":\"verify-20260706\""));
    }

    @Test
    void requestId_缺失时生成安全请求Id() throws Exception {
        HttpResponse<String> response = get("/internal/health", null);

        String requestId = response.headers().firstValue("X-Request-Id").orElse("");
        assertEquals(200, response.statusCode());
        assertTrue(requestId.matches("[A-Za-z0-9._-]{8,128}"));
        assertTrue(response.body().contains("\"requestId\":\"" + requestId + "\""));
    }

    @Test
    void requestId_非法请求Id返回统一Json错误且不回显原值() throws Exception {
        HttpResponse<String> response = get("/internal/health", "bad request id");

        assertEquals(400, response.statusCode());
        assertFalse(response.body().contains("bad request id"));
        assertTrue(response.body().contains("\"code\":400"));
        assertTrue(response.body().contains("\"message\":\"非法 X-Request-Id\""));
        assertNotEquals("bad request id", response.headers().firstValue("X-Request-Id").orElse(""));
    }

    @Test
    void requestId_过长请求Id返回统一Json错误() throws Exception {
        HttpResponse<String> response = get("/internal/health", "a".repeat(129));

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"code\":400"));
        assertTrue(response.body().contains("\"message\":\"非法 X-Request-Id\""));
    }

    @Test
    void notFound_未匹配路径返回统一Json404() throws Exception {
        HttpResponse<String> response = get("/not-found", "not-found-20260706");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("\"code\":404"));
        assertTrue(response.body().contains("\"message\":\"资源不存在\""));
        assertTrue(response.body().contains("\"requestId\":\"not-found-20260706\""));
    }

    @Test
    void failure_未处理异常返回统一Json500() throws Exception {
        HttpResponse<String> response = get("/internal/test-error", "failure-20260706");

        assertEquals(500, response.statusCode());
        assertTrue(response.body().contains("\"code\":500"));
        assertTrue(response.body().contains("\"message\":\"网关内部错误\""));
        assertTrue(response.body().contains("\"requestId\":\"failure-20260706\""));
    }

    @Test
    void close_优雅关闭后监听端口释放() throws Throwable {
        int port = runtime.actualPort();
        VertxTestContext testContext = new VertxTestContext();

        runtime.close()
                .onSuccess(ignored -> {
                    runtime = null;
                    testContext.completeNow();
                })
                .onFailure(testContext::failNow);
        await(testContext);

        assertThrows(IOException.class, () -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 300);
            }
        });
    }

    /**
     * 发送 GET 请求。
     *
     * @param path      请求路径
     * @param requestId 可选请求 ID
     * @return HTTP 响应
     * @throws Exception 请求失败时抛出
     */
    private HttpResponse<String> get(String path, String requestId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + runtime.actualPort() + path))
                .GET()
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(3));
        if (requestId != null) {
            builder.header("X-Request-Id", requestId);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 等待 Vert.x 异步测试完成，并在失败时抛出真实异常。
     *
     * @param testContext Vert.x 测试上下文
     * @throws Throwable 异步流程失败原因
     */
    private void await(VertxTestContext testContext) throws Throwable {
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS));
        if (testContext.failed()) {
            throw testContext.causeOfFailure();
        }
    }
}
