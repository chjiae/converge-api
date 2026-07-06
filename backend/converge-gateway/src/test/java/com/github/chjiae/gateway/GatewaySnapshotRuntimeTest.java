package com.github.chjiae.gateway;

import com.github.chjiae.contract.gateway.GatewayExecutionResourceSnapshot;
import com.github.chjiae.contract.gateway.GatewayPublicModelSnapshot;
import com.github.chjiae.contract.gateway.GatewaySecretEnvelope;
import com.github.chjiae.contract.gateway.GatewaySnapshotChangedEvent;
import com.github.chjiae.contract.gateway.GatewaySnapshotCrypto;
import com.github.chjiae.contract.gateway.GatewaySnapshotJson;
import com.github.chjiae.contract.gateway.GatewaySnapshotManifest;
import com.github.chjiae.contract.gateway.GatewaySnapshotRedisKeys;
import com.github.chjiae.contract.gateway.GatewaySnapshotSchema;
import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;
import com.github.chjiae.gateway.config.GatewayConfig;
import com.github.chjiae.gateway.config.GatewaySnapshotConfig;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关快照运行时测试。
 * 使用 Redis Testcontainer 覆盖启动对账、Pub/Sub 刷新、周期对账补偿、
 * 损坏版本拒绝、last-known-good 保留与空 index 就绪语义。
 */
class GatewaySnapshotRuntimeTest {

    /** 网关投递加密测试密钥，32 字节全 0x03 */
    private static final byte[] SNAPSHOT_ENCRYPTION_KEY = filledKey((byte) 0x03);

    /** 网关 manifest 签名测试密钥，32 字节全 0x04 */
    private static final byte[] SNAPSHOT_SIGNING_KEY = filledKey((byte) 0x04);

    /** 测试用 Redis 容器 */
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    /** HTTP 客户端 */
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /** Vert.x 实例，仅用于测试写 Redis */
    private static final Vertx VERTX = Vertx.vertx();

    /** 当前测试运行时 */
    private GatewayRuntime runtime;

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
        sendRedis(Command.FLUSHDB).toString();
    }

    @AfterAll
    static void closeVertx() {
        VERTX.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
    }

    @Test
    void ready_空TenantIndex视为首次对账成功() throws Exception {
        runtime = GatewayRuntime.start(gatewayConfig(120, 500), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();

        waitUntil(() -> readyStatus() == 200, 3000);
        HttpResponse<String> ready = get("/internal/ready");
        assertEquals(200, ready.statusCode());
        assertTrue(ready.body().contains("\"status\":\"READY\""));
        assertTrue(ready.body().contains("\"indexTenantCount\":0"));
    }

    @Test
    void snapshot_启动加载发布订阅刷新周期补偿并拒绝损坏版本() throws Exception {
        publishSnapshot("tenant-a", 1, true, true);

        runtime = GatewayRuntime.start(gatewayConfig(120, 800), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();

        waitUntil(() -> snapshotStatus().contains("\"loadedTenantCount\":1")
                && snapshotStatus().contains("\"revision\":1"), 3000);
        assertEquals(200, readyStatus());

        publishSnapshot("tenant-a", 2, true, false);
        publishChangedEvent("tenant-a", 2);
        waitUntil(() -> snapshotStatus().contains("\"revision\":2"), 3000);

        publishSnapshot("tenant-a", 3, false, false);
        publishChangedEvent("tenant-a", 3);
        waitUntil(() -> readyStatus() == 503
                && snapshotStatus().contains("\"revision\":2")
                && snapshotStatus().contains("MANIFEST_HMAC_INVALID"), 3000);

        publishSnapshot("tenant-b", 1, true, false);
        waitUntil(() -> snapshotStatus().contains("\"loadedTenantCount\":2")
                && snapshotStatus().contains("\"tenantId\":\"tenant-b\""), 3000);

        HttpResponse<String> status = get("/internal/snapshot-status");
        assertEquals(200, status.statusCode());
        assertFalse(status.body().contains("ciphertext"));
        assertFalse(status.body().contains("nonce"));
        assertFalse(status.body().contains("payloadRedisKey"));
        assertFalse(status.body().contains("manifestHmac"));
    }

    @Test
    void ready_Redis首次对账失败时不可就绪() throws Exception {
        GatewaySnapshotConfig snapshotConfig = new GatewaySnapshotConfig(
                "redis://127.0.0.1:1",
                "gateway-test-key",
                Base64.getEncoder().encodeToString(SNAPSHOT_ENCRYPTION_KEY),
                Base64.getEncoder().encodeToString(SNAPSHOT_SIGNING_KEY),
                120,
                300,
                3
        );
        GatewayConfig config = new GatewayConfig("127.0.0.1", 0, 500,
                "converge-gateway-test", "test-version", snapshotConfig);

        runtime = GatewayRuntime.start(config, true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();

        waitUntil(() -> readyStatus() == 503, 3000);
        assertTrue(get("/internal/health").body().contains("\"status\":\"UP\""));
    }

    private void publishSnapshot(String tenantId, long revision, boolean validHmac, boolean notify) {
        GatewaySecretEnvelope envelope = GatewaySnapshotCrypto.encryptSecret(
                "snapshot-secret-" + tenantId + "-" + revision,
                SNAPSHOT_ENCRYPTION_KEY,
                GatewaySnapshotCrypto.secretAad(GatewaySnapshotSchema.CURRENT_VERSION, tenantId,
                        "resource-" + tenantId, "credential-" + tenantId, revision, "gateway-test-key"),
                "gateway-test-key");

        GatewayTenantSnapshot snapshot = new GatewayTenantSnapshot(
                GatewaySnapshotSchema.CURRENT_VERSION,
                tenantId,
                revision,
                System.currentTimeMillis(),
                List.of(new GatewayPublicModelSnapshot(tenantId, "model-" + tenantId,
                        "public-" + tenantId, "公开模型", "gpt")),
                List.of(new GatewayExecutionResourceSnapshot(tenantId,
                        "resource-" + tenantId,
                        "provider-" + tenantId,
                        "connection-" + tenantId,
                        "credential-" + tenantId,
                        "DIRECT_API",
                        "ENABLED",
                        "OPENAI",
                        "OPENAI_COMPATIBLE",
                        "https://api.example.test/v1/",
                        envelope))
        );

        byte[] payloadBytes = GatewaySnapshotJson.toBytes(snapshot);
        String payloadKey = GatewaySnapshotRedisKeys.payloadKey(tenantId, revision);
        String sha = GatewaySnapshotCrypto.sha256Hex(payloadBytes);
        GatewaySnapshotManifest unsigned = new GatewaySnapshotManifest(
                GatewaySnapshotSchema.CURRENT_VERSION,
                tenantId,
                revision,
                payloadKey,
                sha,
                "",
                "gateway-test-key",
                System.currentTimeMillis());
        String hmac = validHmac
                ? GatewaySnapshotCrypto.signManifest(unsigned, SNAPSHOT_SIGNING_KEY)
                : "invalid-signature";
        GatewaySnapshotManifest manifest = new GatewaySnapshotManifest(unsigned.schemaVersion(),
                unsigned.tenantId(), unsigned.revision(), unsigned.payloadRedisKey(),
                unsigned.payloadSha256Hex(), hmac, unsigned.gatewayKeyId(), unsigned.publishedAtEpochMillis());

        sendRedis(Command.SET, payloadKey, new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8));
        sendRedis(Command.SET, GatewaySnapshotRedisKeys.currentManifestKey(tenantId), GatewaySnapshotJson.toJson(manifest));
        sendRedis(Command.SADD, GatewaySnapshotRedisKeys.tenantIndexKey(), tenantId);
        if (notify) {
            publishChangedEvent(tenantId, revision);
        }
    }

    private void publishChangedEvent(String tenantId, long revision) {
        GatewaySnapshotChangedEvent event = new GatewaySnapshotChangedEvent(
                GatewaySnapshotSchema.CURRENT_VERSION,
                tenantId,
                revision,
                GatewaySnapshotRedisKeys.currentManifestKey(tenantId),
                System.currentTimeMillis());
        sendRedis(Command.PUBLISH, GatewaySnapshotRedisKeys.changedChannel(), GatewaySnapshotJson.toJson(event));
    }

    private GatewayConfig gatewayConfig(long reconcileIntervalMs, long maxStalenessMs) {
        GatewaySnapshotConfig snapshotConfig = new GatewaySnapshotConfig(
                redisUri(),
                "gateway-test-key",
                Base64.getEncoder().encodeToString(SNAPSHOT_ENCRYPTION_KEY),
                Base64.getEncoder().encodeToString(SNAPSHOT_SIGNING_KEY),
                reconcileIntervalMs,
                maxStalenessMs,
                3
        );
        return new GatewayConfig("127.0.0.1", 0, 500,
                "converge-gateway-test", "test-version", snapshotConfig);
    }

    private String redisUri() {
        return "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
    }

    private Response sendRedis(Command command, String... args) {
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

    private int readyStatus() {
        try {
            return get("/internal/ready").statusCode();
        } catch (Exception e) {
            return -1;
        }
    }

    private String snapshotStatus() {
        try {
            return get("/internal/snapshot-status").body();
        } catch (Exception e) {
            return "";
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + runtime.actualPort() + path))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
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

    private static byte[] filledKey(byte value) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, value);
        return key;
    }
}
