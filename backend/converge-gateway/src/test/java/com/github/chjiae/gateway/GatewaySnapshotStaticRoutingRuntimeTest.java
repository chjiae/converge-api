package com.github.chjiae.gateway;

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
import com.github.chjiae.gateway.config.GatewaySnapshotConfig;
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
 * 网关快照静态路由运行时测试。
 * 覆盖 V1 兼容、V2 路由编译、损坏 V2 保留 last-known-good 和状态接口脱敏。
 */
class GatewaySnapshotStaticRoutingRuntimeTest {

    private static final byte[] SNAPSHOT_ENCRYPTION_KEY = filledKey((byte) 0x03);
    private static final byte[] SNAPSHOT_SIGNING_KEY = filledKey((byte) 0x04);
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    private static final Vertx VERTX = Vertx.vertx();
    private final HttpClient httpClient = HttpClient.newHttpClient();
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
        sendRedis(Command.FLUSHDB);
    }

    @AfterAll
    static void closeVertx() {
        VERTX.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
    }

    @Test
    void runtime_兼容V1且V2编译RoutePlan() throws Exception {
        publish(v1Snapshot("tenant-v1", 1));
        publish(v2Snapshot("tenant-v2", 1, true));

        runtime = GatewayRuntime.start(gatewayConfig(), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();

        waitUntil(() -> snapshotStatus().contains("\"loadedTenantCount\":2")
                && snapshotStatus().contains("\"compiledRoutePlanCount\":1"), 3000);

        String status = snapshotStatus();
        assertTrue(status.contains("\"schemaVersion\":1"));
        assertTrue(status.contains("\"schemaVersion\":2"));
        assertTrue(status.contains("\"compiledRoutePlanCount\":1"));
        assertFalse(status.contains("ciphertext"));
        assertFalse(status.contains("nonce"));
        assertFalse(status.contains("manifestHmac"));
    }

    @Test
    void runtime_非法V2拓扑拒绝新版本并保留旧RoutePlan() throws Exception {
        publish(v2Snapshot("tenant-v2", 1, true));

        runtime = GatewayRuntime.start(gatewayConfig(), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        waitUntil(() -> snapshotStatus().contains("\"compiledRoutePlanCount\":1"), 3000);

        publish(v2Snapshot("tenant-v2", 2, false));
        waitUntil(() -> readyStatus() == 503
                && snapshotStatus().contains("\"revision\":1")
                && snapshotStatus().contains("STATIC_ROUTE_INVALID"), 3000);
    }

    private GatewayTenantSnapshot v1Snapshot(String tenantId, long revision) {
        GatewaySecretEnvelope envelope = envelope(tenantId, revision, "res-1", "cred-1", GatewaySnapshotSchema.VERSION_1);
        return new GatewayTenantSnapshot(GatewaySnapshotSchema.VERSION_1, tenantId, revision, System.currentTimeMillis(),
                List.of(new GatewayPublicModelSnapshot(tenantId, "model-1", "public-chat", "公开模型", "chat")),
                List.of(new GatewayExecutionResourceSnapshot(tenantId, "res-1", "provider-1", "conn-1",
                        "cred-1", "DIRECT_API", "ENABLED", "OPENAI", "OPENAI_COMPATIBLE",
                        "https://api.example.test/v1/", envelope)));
    }

    private GatewayTenantSnapshot v2Snapshot(String tenantId, long revision, boolean validTopology) {
        GatewaySecretEnvelope envelope = envelope(tenantId, revision, "res-1", "cred-1", GatewaySnapshotSchema.VERSION_2);
        return new GatewayTenantSnapshot(GatewaySnapshotSchema.VERSION_2, tenantId, revision, System.currentTimeMillis(),
                List.of(new GatewayPublicModelSnapshot(tenantId, "model-1", "public-chat", "公开模型", "chat")),
                List.of(new GatewayExecutionResourceSnapshot(tenantId, "res-1", "provider-1", "conn-1",
                        "cred-1", "DIRECT_API", "ENABLED", "OPENAI", "OPENAI_COMPATIBLE",
                        "https://api.example.test/v1/", envelope)),
                List.of(new GatewayResourcePoolSnapshot(tenantId, "pool-1", "primary", "主池",
                        "ENABLED", "PRIORITY_WEIGHTED", List.of(
                        new GatewayResourcePoolMemberSnapshot(tenantId, "pool-1", "res-1",
                                "ENABLED", 100, 100)))),
                validTopology
                        ? List.of(new GatewayResourceModelBindingSnapshot(tenantId, "res-1", "model-1",
                        "CHAT_COMPLETIONS", "upstream-chat", "ENABLED"))
                        : List.of(),
                List.of(new GatewayRoutePolicySnapshot(tenantId, "policy-1", "model-1", "public-chat",
                        "CHAT_COMPLETIONS", "ENABLED", "PRIORITY_WEIGHTED", List.of(
                        new GatewayRouteTargetSnapshot(tenantId, "policy-1", "pool-1",
                                "ENABLED", 100, 100)))));
    }

    private GatewaySecretEnvelope envelope(String tenantId, long revision, String resourceId,
                                           String credentialId, int schemaVersion) {
        return GatewaySnapshotCrypto.encryptSecret("runtime-secret", SNAPSHOT_ENCRYPTION_KEY,
                GatewaySnapshotCrypto.secretAad(schemaVersion, tenantId, resourceId, credentialId,
                        revision, "gateway-test-key"),
                "gateway-test-key");
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
        sendRedis(Command.SET, payloadKey, new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8));
        sendRedis(Command.SET, GatewaySnapshotRedisKeys.currentManifestKey(snapshot.tenantId()),
                GatewaySnapshotJson.toJson(manifest));
        sendRedis(Command.SADD, GatewaySnapshotRedisKeys.tenantIndexKey(), snapshot.tenantId());
    }

    private GatewayConfig gatewayConfig() {
        GatewaySnapshotConfig snapshotConfig = new GatewaySnapshotConfig(
                redisUri(),
                "gateway-test-key",
                Base64.getEncoder().encodeToString(SNAPSHOT_ENCRYPTION_KEY),
                Base64.getEncoder().encodeToString(SNAPSHOT_SIGNING_KEY),
                120,
                800,
                3
        );
        return new GatewayConfig("127.0.0.1", 0, 500,
                "converge-gateway-test", "test-version", snapshotConfig);
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

    private static String redisUri() {
        return "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
    }

    private static byte[] filledKey(byte value) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, value);
        return key;
    }
}
