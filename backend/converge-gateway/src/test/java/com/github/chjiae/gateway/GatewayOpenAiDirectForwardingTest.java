package com.github.chjiae.gateway;

import com.github.chjiae.contract.gateway.GatewayAccessGroupModelGrantSnapshot;
import com.github.chjiae.contract.gateway.GatewayAccessGroupSnapshot;
import com.github.chjiae.contract.gateway.GatewayClientApiKeyAccessGroupSnapshot;
import com.github.chjiae.contract.gateway.GatewayClientApiKeySnapshot;
import com.github.chjiae.contract.gateway.GatewayClientKeyCrypto;
import com.github.chjiae.contract.gateway.GatewayExecutionResourceSnapshot;
import com.github.chjiae.contract.gateway.GatewayPublicModelSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourceModelBindingSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourcePoolMemberSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourcePoolSnapshot;
import com.github.chjiae.contract.gateway.GatewayRoutePolicySnapshot;
import com.github.chjiae.contract.gateway.GatewayRouteTargetSnapshot;
import com.github.chjiae.contract.gateway.GatewaySecretEnvelope;
import com.github.chjiae.contract.gateway.GatewaySnapshotCrypto;
import com.github.chjiae.contract.gateway.GatewaySnapshotJson;
import com.github.chjiae.contract.gateway.GatewaySnapshotManifest;
import com.github.chjiae.contract.gateway.GatewaySnapshotRedisKeys;
import com.github.chjiae.contract.gateway.GatewaySnapshotSchema;
import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;
import com.github.chjiae.gateway.config.GatewayConfig;
import com.github.chjiae.gateway.config.GatewayExecutionConfig;
import com.github.chjiae.gateway.config.GatewaySnapshotConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI Compatible Direct API 端到端转发测试。
 * 上游使用本地 HTTP Server，不调用任何真实外部 Provider。
 */
class GatewayOpenAiDirectForwardingTest {

    private static final byte[] SNAPSHOT_ENCRYPTION_KEY = filledKey((byte) 0x03);
    private static final byte[] SNAPSHOT_SIGNING_KEY = filledKey((byte) 0x04);
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    private static final Vertx VERTX = Vertx.vertx();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private GatewayRuntime runtime;
    private HttpServer upstream;
    private GatewayClientKeyCrypto.GeneratedClientKey key;
    private AtomicInteger upstreamCalls;
    private AtomicReference<String> upstreamAuthorization;
    private AtomicReference<String> upstreamRequestBody;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
    }

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
            runtime = null;
        }
        if (upstream != null) {
            upstream.stop(0);
            upstream = null;
        }
        sendRedis(Command.FLUSHDB);
    }

    @AfterAll
    static void closeVertx() {
        VERTX.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
    }

    @Test
    void chat_非流式成功时改写请求与响应模型且注入上游Bearer() throws Exception {
        startUpstream(exchange -> {
            capture(exchange);
            writeJson(exchange, 200, "{\"id\":\"cmpl-1\",\"model\":\"upstream-chat\",\"choices\":[]}");
        });
        startGateway(true, true, true, "ENABLED", System.currentTimeMillis() + 3_600_000L, 4096);

        HttpResponse<String> response = postJson("{\"model\":\"public-chat\",\"messages\":[],\"stream\":false,"
                + "\"metadata\":{\"trace\":\"safe\"}}", key.rawKey());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"model\":\"public-chat\""));
        assertFalse(response.body().contains("upstream-chat"));
        assertEquals(1, upstreamCalls.get());
        assertTrue(upstreamAuthorization.get().startsWith("Bearer "));
        assertFalse(upstreamAuthorization.get().contains(key.rawKey()));
        assertTrue(upstreamRequestBody.get().contains("\"model\":\"upstream-chat\""));
        assertTrue(upstreamRequestBody.get().contains("\"metadata\":{\"trace\":\"safe\"}"));
        assertFalse(upstreamRequestBody.get().contains("public-chat"));
    }

    @Test
    void chat_无授权或无Route时返回安全错误且不请求上游() throws Exception {
        startUpstream(exchange -> {
            capture(exchange);
            writeJson(exchange, 200, "{\"model\":\"upstream-chat\"}");
        });
        startGateway(false, true, true, "ENABLED", System.currentTimeMillis() + 3_600_000L, 4096);

        HttpResponse<String> denied = postJson("{\"model\":\"public-chat\",\"messages\":[]}", key.rawKey());

        assertEquals(403, denied.statusCode());
        assertTrue(denied.body().contains("\"code\":\"model_access_denied\""));
        assertEquals(0, upstreamCalls.get());

        runtime.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        runtime = null;
        sendRedis(Command.FLUSHDB);
        key = null;
        startGateway(true, false, true, "ENABLED", System.currentTimeMillis() + 3_600_000L, 4096);

        HttpResponse<String> noRoute = postJson("{\"model\":\"public-chat\",\"messages\":[]}", key.rawKey());

        assertEquals(404, noRoute.statusCode());
        assertTrue(noRoute.body().contains("\"code\":\"model_not_found\""));
        assertEquals(0, upstreamCalls.get());
    }

    @Test
    void chat_请求校验与Key状态错误映射() throws Exception {
        startUpstream(exchange -> {
            capture(exchange);
            writeJson(exchange, 200, "{\"model\":\"upstream-chat\"}");
        });
        startGateway(true, true, true, "DISABLED", System.currentTimeMillis() + 3_600_000L, 32);

        HttpResponse<String> invalidKey = postJson("{\"model\":\"public-chat\",\"messages\":[]}", key.rawKey());
        assertEquals(401, invalidKey.statusCode());
        assertEquals(0, upstreamCalls.get());

        runtime.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        runtime = null;
        sendRedis(Command.FLUSHDB);
        key = null;
        startGateway(true, true, true, "ENABLED", System.currentTimeMillis() + 3_600_000L, 128);

        HttpResponse<String> unsupportedType = post("{\"model\":\"public-chat\"}", key.rawKey(), "text/plain");
        assertEquals(415, unsupportedType.statusCode());
        HttpResponse<String> invalidJson = postJson("{", key.rawKey());
        assertEquals(400, invalidJson.statusCode());
        HttpResponse<String> invalidStream = postJson("{\"model\":\"public-chat\",\"stream\":\"true\"}", key.rawKey());
        assertEquals(400, invalidStream.statusCode());
        HttpResponse<String> tooLarge = postJson("{\"model\":\"public-chat\",\"messages\":[\""
                        + "012345678901234567890123456789012345678901234567890123456789"
                        + "012345678901234567890123456789012345678901234567890123456789"
                        + "\"]}",
                key.rawKey());
        assertEquals(413, tooLarge.statusCode());
        assertEquals(0, upstreamCalls.get());
    }

    @Test
    void chat_上游错误安全映射且不回显错误Body() throws Exception {
        startUpstream(exchange -> {
            capture(exchange);
            writeJson(exchange, 401, "{\"error\":\"upstream-secret-error-body\"}");
        });
        startGateway(true, true, true, "ENABLED", System.currentTimeMillis() + 3_600_000L, 4096);

        HttpResponse<String> response = postJson("{\"model\":\"public-chat\",\"messages\":[]}", key.rawKey());

        assertEquals(502, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"upstream_authentication_failed\""));
        assertFalse(response.body().contains("upstream-secret-error-body"));
    }

    @Test
    void chat_上游限流连接失败超时和未就绪均安全映射() throws Exception {
        startUpstream(exchange -> {
            capture(exchange);
            writeJson(exchange, 429, "{\"error\":\"rate-limited-body\"}");
        });
        startGateway(true, true, true, "ENABLED", System.currentTimeMillis() + 3_600_000L,
                4096, 1000, 5000);

        HttpResponse<String> limited = postJson("{\"model\":\"public-chat\",\"messages\":[]}", key.rawKey());
        assertEquals(429, limited.statusCode());
        assertTrue(limited.body().contains("\"code\":\"upstream_rate_limited\""));
        assertFalse(limited.body().contains("rate-limited-body"));

        runtime.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        runtime = null;
        upstream.stop(0);
        upstream = null;
        sendRedis(Command.FLUSHDB);
        key = null;
        startUpstream(exchange -> {
            capture(exchange);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            writeJson(exchange, 200, "{\"model\":\"upstream-chat\"}");
        });
        startGateway(true, true, true, "ENABLED", System.currentTimeMillis() + 3_600_000L,
                4096, 1000, 100);

        HttpResponse<String> timeout = postJson("{\"model\":\"public-chat\",\"messages\":[]}", key.rawKey());
        assertEquals(504, timeout.statusCode());
        assertTrue(timeout.body().contains("\"code\":\"upstream_timeout\""));

        runtime.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        runtime = null;
        upstream.stop(0);
        upstream = null;
        sendRedis(Command.FLUSHDB);
        key = null;
        startUpstream(exchange -> {
            capture(exchange);
            writeJson(exchange, 200, "{\"model\":\"upstream-chat\"}");
        });
        key = GatewayClientKeyCrypto.generate();
        publish(v3Snapshot(true, true, true, "ENABLED", System.currentTimeMillis() + 3_600_000L));
        upstream.stop(0);
        upstream = null;
        runtime = GatewayRuntime.start(gatewayConfig(4096, 1000, 5000), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        waitUntil(() -> snapshotStatus().contains("\"clientKeyCount\":1"), 3000);

        HttpResponse<String> connectionError = postJson("{\"model\":\"public-chat\",\"messages\":[]}", key.rawKey());
        assertEquals(502, connectionError.statusCode());
        assertTrue(connectionError.body().contains("\"code\":\"upstream_connection_error\""));

        runtime.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        runtime = null;
        sendRedis(Command.FLUSHDB);
        runtime = GatewayRuntime.start(gatewayConfig("redis://127.0.0.1:1", 4096, 1000, 5000), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();

        HttpResponse<String> notReady = postJson("{\"model\":\"public-chat\",\"messages\":[]}", key.rawKey());
        assertEquals(503, notReady.statusCode());
        assertTrue(notReady.body().contains("\"code\":\"gateway_not_ready\""));
    }

    @Test
    void chat_SSE按Event重写模型并透传Done() throws Exception {
        startUpstream(exchange -> {
            capture(exchange);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            byte[] chunkOne = "data: {\"id\":\"cmpl-1\",\"model\":\"up".getBytes(StandardCharsets.UTF_8);
            byte[] chunkTwo = "stream-chat\",\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\r\n\r\n".getBytes(StandardCharsets.UTF_8);
            byte[] chunkThree = "data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseBody().write(chunkOne);
            exchange.getResponseBody().flush();
            exchange.getResponseBody().write(chunkTwo);
            exchange.getResponseBody().flush();
            exchange.getResponseBody().write(chunkThree);
            exchange.close();
        });
        startGateway(true, true, true, "ENABLED", System.currentTimeMillis() + 3_600_000L, 4096);

        HttpResponse<String> response = postJson("{\"model\":\"public-chat\",\"messages\":[],\"stream\":true}", key.rawKey());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/event-stream"));
        assertTrue(response.body().contains("\"model\":\"public-chat\""));
        assertTrue(response.body().contains("data: [DONE]"));
        assertFalse(response.body().contains("upstream-chat"));
    }

    private void startGateway(boolean includeGrant, boolean includeRoute, boolean includeBinding,
                              String keyStatus, long expiresAt, long maxRequestBytes) throws Exception {
        startGateway(includeGrant, includeRoute, includeBinding, keyStatus, expiresAt,
                maxRequestBytes, 1000, 5000);
    }

    private void startGateway(boolean includeGrant, boolean includeRoute, boolean includeBinding,
                              String keyStatus, long expiresAt, long maxRequestBytes,
                              long connectTimeoutMs, long idleTimeoutMs) throws Exception {
        if (key == null) {
            key = GatewayClientKeyCrypto.generate();
        }
        publish(v3Snapshot(includeGrant, includeRoute, includeBinding, keyStatus, expiresAt));
        runtime = GatewayRuntime.start(gatewayConfig(maxRequestBytes, connectTimeoutMs, idleTimeoutMs), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        waitUntil(() -> snapshotStatus().contains("\"clientKeyCount\":1"), 3000);
    }

    private void startUpstream(ExchangeHandler handler) throws IOException {
        upstreamCalls = new AtomicInteger();
        upstreamAuthorization = new AtomicReference<>("");
        upstreamRequestBody = new AtomicReference<>("");
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/v1/chat/completions", handler::handle);
        upstream.start();
    }

    private void capture(HttpExchange exchange) throws IOException {
        upstreamCalls.incrementAndGet();
        upstreamAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        exchange.getRequestBody().transferTo(body);
        upstreamRequestBody.set(body.toString(StandardCharsets.UTF_8));
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private HttpResponse<String> postJson(String body, String rawKey) throws Exception {
        return post(body, rawKey, "application/json; charset=utf-8");
    }

    private HttpResponse<String> post(String body, String rawKey, String contentType) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + runtime.actualPort() + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (rawKey != null) {
            builder.header("Authorization", "Bearer " + rawKey);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private GatewayTenantSnapshot v3Snapshot(boolean includeGrant, boolean includeRoute, boolean includeBinding,
                                             String keyStatus, long expiresAt) {
        GatewaySecretEnvelope envelope = GatewaySnapshotCrypto.encryptSecret("runtime-secret",
                SNAPSHOT_ENCRYPTION_KEY,
                GatewaySnapshotCrypto.secretAad(GatewaySnapshotSchema.VERSION_3, "tenant-1", "res-1",
                        "cred-1", 1, "gateway-test-key"),
                "gateway-test-key");
        byte[] salt = GatewayClientKeyCrypto.generateSalt();
        byte[] verifier = GatewayClientKeyCrypto.verifier(key.rawKey(), key.keyId(), 1, salt);
        List<GatewayAccessGroupModelGrantSnapshot> grants = includeGrant
                ? List.of(new GatewayAccessGroupModelGrantSnapshot("tenant-1", "grant-1", "group-1",
                "model-1", "public-chat", "CHAT_COMPLETIONS", "ENABLED"))
                : List.of();
        List<GatewayResourceModelBindingSnapshot> bindings = includeBinding
                ? List.of(new GatewayResourceModelBindingSnapshot("tenant-1", "res-1", "model-1",
                "CHAT_COMPLETIONS", "upstream-chat", "ENABLED"))
                : List.of();
        List<GatewayRoutePolicySnapshot> policies = includeRoute
                ? List.of(new GatewayRoutePolicySnapshot("tenant-1", "policy-1", "model-1", "public-chat",
                "CHAT_COMPLETIONS", "ENABLED", "PRIORITY_WEIGHTED", List.of(
                new GatewayRouteTargetSnapshot("tenant-1", "policy-1", "pool-1", "ENABLED", 100, 100))))
                : List.of();
        return new GatewayTenantSnapshot(GatewaySnapshotSchema.VERSION_3, "tenant-1", 1, System.currentTimeMillis(),
                List.of(new GatewayPublicModelSnapshot("tenant-1", "model-1", "public-chat", "公开模型", "chat")),
                List.of(new GatewayExecutionResourceSnapshot("tenant-1", "res-1", "provider-1", "conn-1",
                        "cred-1", "DIRECT_API", "ENABLED", "OPENAI", "OPENAI_COMPATIBLE",
                        "http://127.0.0.1:" + upstream.getAddress().getPort() + "/v1/", envelope)),
                List.of(new GatewayResourcePoolSnapshot("tenant-1", "pool-1", "primary", "主池",
                        "ENABLED", "PRIORITY_WEIGHTED", List.of(
                        new GatewayResourcePoolMemberSnapshot("tenant-1", "pool-1", "res-1",
                                "ENABLED", 100, 100)))),
                bindings,
                policies,
                List.of(new GatewayAccessGroupSnapshot("tenant-1", "group-1", "default", "ENABLED")),
                grants,
                List.of(new GatewayClientApiKeySnapshot("tenant-1", "key-1", key.keyId(),
                        keyStatus, "SHA-256",
                        Base64.getEncoder().encodeToString(salt),
                        Base64.getEncoder().encodeToString(verifier),
                        1, expiresAt)),
                List.of(new GatewayClientApiKeyAccessGroupSnapshot("tenant-1", "binding-1",
                        "key-1", "group-1", "ENABLED")));
    }

    private void publish(GatewayTenantSnapshot snapshot) {
        byte[] payloadBytes = GatewaySnapshotJson.toBytes(snapshot);
        String payloadKey = GatewaySnapshotRedisKeys.payloadKey(snapshot.tenantId(), snapshot.revision());
        GatewaySnapshotManifest unsigned = new GatewaySnapshotManifest(snapshot.schemaVersion(), snapshot.tenantId(),
                snapshot.revision(), payloadKey, GatewaySnapshotCrypto.sha256Hex(payloadBytes), "",
                "gateway-test-key", System.currentTimeMillis());
        String hmac = GatewaySnapshotCrypto.signManifest(unsigned, SNAPSHOT_SIGNING_KEY);
        GatewaySnapshotManifest manifest = new GatewaySnapshotManifest(unsigned.schemaVersion(), unsigned.tenantId(),
                unsigned.revision(), unsigned.payloadRedisKey(), unsigned.payloadSha256Hex(), hmac,
                unsigned.gatewayKeyId(), unsigned.publishedAtEpochMillis());
        sendRedis(Command.SET, payloadKey, new String(payloadBytes, StandardCharsets.UTF_8));
        sendRedis(Command.SET, GatewaySnapshotRedisKeys.currentManifestKey(snapshot.tenantId()),
                GatewaySnapshotJson.toJson(manifest));
        sendRedis(Command.SADD, GatewaySnapshotRedisKeys.tenantIndexKey(), snapshot.tenantId());
    }

    private GatewayConfig gatewayConfig(long maxRequestBytes) {
        return gatewayConfig(maxRequestBytes, 1000, 5000);
    }

    private GatewayConfig gatewayConfig(long maxRequestBytes, long connectTimeoutMs, long idleTimeoutMs) {
        return gatewayConfig(redisUri(), maxRequestBytes, connectTimeoutMs, idleTimeoutMs);
    }

    private GatewayConfig gatewayConfig(String redisUri, long maxRequestBytes,
                                        long connectTimeoutMs, long idleTimeoutMs) {
        GatewaySnapshotConfig snapshotConfig = new GatewaySnapshotConfig(
                redisUri,
                "gateway-test-key",
                Base64.getEncoder().encodeToString(SNAPSHOT_ENCRYPTION_KEY),
                Base64.getEncoder().encodeToString(SNAPSHOT_SIGNING_KEY),
                120,
                800,
                3
        );
        GatewayExecutionConfig executionConfig = new GatewayExecutionConfig(connectTimeoutMs, idleTimeoutMs, 10,
                maxRequestBytes, 1024 * 1024, 4096, 4096);
        return new GatewayConfig("127.0.0.1", 0, 500,
                "converge-gateway-test", "test-version", snapshotConfig, executionConfig);
    }

    private String snapshotStatus() {
        try {
            return httpClient.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + runtime.actualPort() + "/internal/snapshot-status"))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            return "";
        }
    }

    private void waitUntil(BooleanSupplier supplier, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (supplier.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        assertTrue(supplier.getAsBoolean(), "条件在超时前未满足");
    }

    private static Response sendRedis(Command command, String... args) {
        Redis redis = Redis.createClient(VERTX, new RedisOptions().setConnectionString(redisUri()));
        Request request = Request.cmd(command);
        for (String arg : args) {
            request.arg(arg);
        }
        try {
            return redis.connect()
                    .compose(connection -> connection.send(request)
                            .eventually(() -> connection.close()))
                    .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        } finally {
            redis.close();
        }
    }

    private static String redisUri() {
        return "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
    }

    private static byte[] filledKey(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }

    private interface ExchangeHandler {

        /**
         * 处理上游测试请求。
         *
         * @param exchange HTTP 交换对象
         * @throws IOException 写响应失败时抛出
         */
        void handle(HttpExchange exchange) throws IOException;
    }
}
