package com.github.chjiae.gateway;

import com.github.chjiae.contract.gateway.GatewayAccessGroupModelGrantSnapshot;
import com.github.chjiae.contract.gateway.GatewayAccessGroupSnapshot;
import com.github.chjiae.contract.gateway.GatewayClientApiKeyAccessGroupSnapshot;
import com.github.chjiae.contract.gateway.GatewayClientApiKeySnapshot;
import com.github.chjiae.contract.gateway.GatewayClientKeyCrypto;
import com.github.chjiae.contract.gateway.GatewayExecutionResourceRuntimePolicySnapshot;
import com.github.chjiae.contract.gateway.GatewayExecutionResourceSnapshot;
import com.github.chjiae.contract.gateway.GatewayPublicModelSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourceModelBindingSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourcePoolMemberSnapshot;
import com.github.chjiae.contract.gateway.GatewayResourcePoolSnapshot;
import com.github.chjiae.contract.gateway.GatewayRoutePolicySnapshot;
import com.github.chjiae.contract.gateway.GatewayRouteTargetSnapshot;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关快照增量刷新测试。
 * 覆盖 Pub/Sub tenant 定向刷新、revision skip、key index 增量替换与周期全量兜底。
 */
class GatewaySnapshotIncrementalRefreshTest {

    /** 网关投递加密测试密钥，32 字节全 0x03。 */
    private static final byte[] SNAPSHOT_ENCRYPTION_KEY = filledKey((byte) 0x03);

    /** 网关 manifest 签名测试密钥，32 字节全 0x04。 */
    private static final byte[] SNAPSHOT_SIGNING_KEY = filledKey((byte) 0x04);

    /** Redis Testcontainer。 */
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    /** 测试用 Vert.x，仅用于 Redis 写入。 */
    private static final Vertx VERTX = Vertx.vertx();

    /** HTTP 客户端。 */
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /** 当前测试网关。 */
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
    void pubsub_只刷新目标租户并增量替换KeyIndex() throws Exception {
        GatewayClientKeyCrypto.GeneratedClientKey tenantAOldKey = GatewayClientKeyCrypto.generate();
        GatewayClientKeyCrypto.GeneratedClientKey tenantANewKey =
                GatewayClientKeyCrypto.generateForKeyId(tenantAOldKey.keyId());
        GatewayClientKeyCrypto.GeneratedClientKey tenantBKey = GatewayClientKeyCrypto.generate();
        publishSnapshot(v4Snapshot("tenant-a", 1, tenantAOldKey, 1, "ENABLED",
                System.currentTimeMillis() + 3_600_000L, 1));
        publishSnapshot(v4Snapshot("tenant-b", 1, tenantBKey, 1, "ENABLED",
                System.currentTimeMillis() + 3_600_000L, 1));

        runtime = GatewayRuntime.start(gatewayConfig(5_000, 3_000, 20, 16, 65_536), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        waitUntil(() -> snapshotStatus().contains("\"clientKeyCount\":2"), 3_000);
        assertEquals(200, getModels(tenantAOldKey.rawKey()).statusCode());
        assertEquals(200, getModels(tenantBKey.rawKey()).statusCode());

        publishSnapshot(v4Snapshot("tenant-a", 2, tenantANewKey, 2, "ENABLED",
                System.currentTimeMillis() + 3_600_000L, 2));
        publishChangedEvent("tenant-a", 2);

        waitUntil(() -> snapshotStatus().contains("\"tenantId\":\"tenant-a\"")
                && snapshotStatus().contains("\"revision\":2")
                && snapshotStatus().contains("\"lastTenantRefreshEpochMillis\":")
                && modelsStatus(tenantANewKey.rawKey()) == 200, 3_000);
        assertEquals(401, getModels(tenantAOldKey.rawKey()).statusCode());
        assertEquals(200, getModels(tenantBKey.rawKey()).statusCode());
        String status = snapshotStatus();
        assertTrue(status.contains("\"tenantId\":\"tenant-b\""));
        assertTrue(status.contains("\"revision\":1"));
        assertTrue(status.contains("\"loadedClientKeyCount\":2"));
        assertTrue(status.contains("\"tenantIndexCount\":2"));
        assertTrue(status.contains("\"loadedRuntimePolicyCount\":2"));
    }

    @Test
    void pubsub_重复Revision跳过Payload且非法事件不触发全量对账() throws Exception {
        GatewayClientKeyCrypto.GeneratedClientKey tenantAKey = GatewayClientKeyCrypto.generate();
        GatewayClientKeyCrypto.GeneratedClientKey tenantBKey = GatewayClientKeyCrypto.generate();
        publishSnapshot(v4Snapshot("tenant-a", 1, tenantAKey, 1, "ENABLED",
                System.currentTimeMillis() + 3_600_000L, 1));

        runtime = GatewayRuntime.start(gatewayConfig(5_000, 3_000, 20, 16, 65_536), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        waitUntil(() -> snapshotStatus().contains("\"loadedTenantCount\":1"), 3_000);

        publishSnapshot(v4Snapshot("tenant-b", 1, tenantBKey, 1, "ENABLED",
                System.currentTimeMillis() + 3_600_000L, 1));
        publishRawChangedEvent("{\"bad\":true}");
        Thread.sleep(400);
        assertFalse(snapshotStatus().contains("\"tenantId\":\"tenant-b\""));

        publishManifestWithMissingPayload("tenant-a", 1);
        publishChangedEvent("tenant-a", 1);

        waitUntil(() -> snapshotStatus().contains("\"snapshotRefreshSkippedCount\":1"), 3_000);
        String status = snapshotStatus();
        assertTrue(status.contains("\"revision\":1"));
        assertFalse(status.contains("PAYLOAD_MISSING"));
        assertEquals(200, getModels(tenantAKey.rawKey()).statusCode());
    }

    @Test
    void periodic_全量对账仍会删除TenantIndex移除的本地状态() throws Exception {
        GatewayClientKeyCrypto.GeneratedClientKey tenantAKey = GatewayClientKeyCrypto.generate();
        GatewayClientKeyCrypto.GeneratedClientKey tenantBKey = GatewayClientKeyCrypto.generate();
        publishSnapshot(v4Snapshot("tenant-a", 1, tenantAKey, 1, "ENABLED",
                System.currentTimeMillis() + 3_600_000L, 1));
        publishSnapshot(v4Snapshot("tenant-b", 1, tenantBKey, 1, "ENABLED",
                System.currentTimeMillis() + 3_600_000L, 1));

        runtime = GatewayRuntime.start(gatewayConfig(120, 3_000, 20, 16, 65_536), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        waitUntil(() -> snapshotStatus().contains("\"loadedTenantCount\":2")
                && modelsStatus(tenantBKey.rawKey()) == 200, 3_000);

        sendRedis(Command.SREM, GatewaySnapshotRedisKeys.tenantIndexKey(), "tenant-b");

        waitUntil(() -> snapshotStatus().contains("\"loadedTenantCount\":1")
                && snapshotStatus().contains("\"loadedClientKeyCount\":1"), 3_000);
        assertEquals(200, getModels(tenantAKey.rawKey()).statusCode());
        assertEquals(401, getModels(tenantBKey.rawKey()).statusCode());
        assertFalse(snapshotStatus().contains("\"tenantId\":\"tenant-b\""));
        assertTrue(snapshotStatus().contains("\"lastFullReconcileEpochMillis\":"));
    }

    private GatewayTenantSnapshot v4Snapshot(String tenantId,
                                             long revision,
                                             GatewayClientKeyCrypto.GeneratedClientKey key,
                                             int keyVersion,
                                             String keyStatus,
                                             long expiresAt,
                                             long policyVersion) {
        String modelId = "model-" + tenantId;
        String resourceId = "res-" + tenantId;
        String credentialId = "cred-" + tenantId;
        GatewaySecretEnvelope envelope = GatewaySnapshotCrypto.encryptSecret("runtime-secret-" + tenantId,
                SNAPSHOT_ENCRYPTION_KEY,
                GatewaySnapshotCrypto.secretAad(GatewaySnapshotSchema.VERSION_4, tenantId, resourceId,
                        credentialId, revision, "gateway-test-key"),
                "gateway-test-key");
        byte[] salt = GatewayClientKeyCrypto.generateSalt();
        byte[] verifier = GatewayClientKeyCrypto.verifier(key.rawKey(), key.keyId(), keyVersion, salt);
        return new GatewayTenantSnapshot(GatewaySnapshotSchema.VERSION_4, tenantId, revision, System.currentTimeMillis(),
                List.of(new GatewayPublicModelSnapshot(tenantId, modelId, "public-chat-" + tenantId,
                        "公开模型", "chat")),
                List.of(new GatewayExecutionResourceSnapshot(tenantId, resourceId, "provider-" + tenantId,
                        "conn-" + tenantId, credentialId, "DIRECT_API", "ENABLED", "OPENAI",
                        "OPENAI_COMPATIBLE", "https://api.example.test/v1/", envelope)),
                List.of(new GatewayResourcePoolSnapshot(tenantId, "pool-" + tenantId, "primary",
                        "主池", "ENABLED", "PRIORITY_WEIGHTED", List.of(
                        new GatewayResourcePoolMemberSnapshot(tenantId, "pool-" + tenantId, resourceId,
                                "ENABLED", 100, 100)))),
                List.of(new GatewayResourceModelBindingSnapshot(tenantId, resourceId, modelId,
                        "CHAT_COMPLETIONS", "upstream-chat", "ENABLED")),
                List.of(new GatewayRoutePolicySnapshot(tenantId, "policy-" + tenantId, modelId,
                        "public-chat-" + tenantId, "CHAT_COMPLETIONS", "ENABLED", "PRIORITY_WEIGHTED",
                        List.of(new GatewayRouteTargetSnapshot(tenantId, "policy-" + tenantId,
                                "pool-" + tenantId, "ENABLED", 100, 100)))),
                List.of(new GatewayAccessGroupSnapshot(tenantId, "group-" + tenantId, "default", "ENABLED")),
                List.of(new GatewayAccessGroupModelGrantSnapshot(tenantId, "grant-" + tenantId,
                        "group-" + tenantId, modelId, "public-chat-" + tenantId,
                        "CHAT_COMPLETIONS", "ENABLED")),
                List.of(new GatewayClientApiKeySnapshot(tenantId, "key-" + tenantId, key.keyId(),
                        keyStatus, "SHA-256", Base64.getEncoder().encodeToString(salt),
                        Base64.getEncoder().encodeToString(verifier), keyVersion, expiresAt)),
                List.of(new GatewayClientApiKeyAccessGroupSnapshot(tenantId, "key-group-" + tenantId,
                        "key-" + tenantId, "group-" + tenantId, "ENABLED")),
                List.of(new GatewayExecutionResourceRuntimePolicySnapshot(tenantId, "runtime-policy-" + tenantId,
                        resourceId, policyVersion, 0, 3, 60_000L, 30_000L, 60_000L)));
    }

    private void publishSnapshot(GatewayTenantSnapshot snapshot) {
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

    private void publishManifestWithMissingPayload(String tenantId, long revision) {
        String payloadKey = GatewaySnapshotRedisKeys.payloadKey(tenantId, revision) + ":missing";
        GatewaySnapshotManifest unsigned = new GatewaySnapshotManifest(GatewaySnapshotSchema.VERSION_4, tenantId,
                revision, payloadKey, "00", "", "gateway-test-key", System.currentTimeMillis());
        String hmac = GatewaySnapshotCrypto.signManifest(unsigned, SNAPSHOT_SIGNING_KEY);
        GatewaySnapshotManifest manifest = new GatewaySnapshotManifest(unsigned.schemaVersion(), unsigned.tenantId(),
                unsigned.revision(), unsigned.payloadRedisKey(), unsigned.payloadSha256Hex(), hmac,
                unsigned.gatewayKeyId(), unsigned.publishedAtEpochMillis());
        sendRedis(Command.SET, GatewaySnapshotRedisKeys.currentManifestKey(tenantId), GatewaySnapshotJson.toJson(manifest));
    }

    private void publishChangedEvent(String tenantId, long revision) {
        GatewaySnapshotChangedEvent event = new GatewaySnapshotChangedEvent(GatewaySnapshotSchema.VERSION_4,
                tenantId, revision, GatewaySnapshotRedisKeys.currentManifestKey(tenantId), System.currentTimeMillis());
        publishRawChangedEvent(GatewaySnapshotJson.toJson(event));
    }

    private void publishRawChangedEvent(String payload) {
        sendRedis(Command.PUBLISH, GatewaySnapshotRedisKeys.changedChannel(), payload);
    }

    private GatewayConfig gatewayConfig(long reconcileIntervalMs,
                                        long maxStalenessMs,
                                        long debounceMs,
                                        int maxPending,
                                        int maxEventBytes) {
        GatewaySnapshotConfig snapshotConfig = new GatewaySnapshotConfig(
                redisUri(),
                "gateway-test-key",
                Base64.getEncoder().encodeToString(SNAPSHOT_ENCRYPTION_KEY),
                Base64.getEncoder().encodeToString(SNAPSHOT_SIGNING_KEY),
                reconcileIntervalMs,
                maxStalenessMs,
                3,
                debounceMs,
                maxPending,
                maxEventBytes
        );
        return new GatewayConfig("127.0.0.1", 0, 500,
                "converge-gateway-test", "test-version", snapshotConfig);
    }

    private HttpResponse<String> getModels(String rawKey) throws Exception {
        return httpClient.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + runtime.actualPort() + "/v1/models"))
                        .timeout(Duration.ofSeconds(3))
                        .header("Authorization", "Bearer " + rawKey)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private int modelsStatus(String rawKey) {
        try {
            return getModels(rawKey).statusCode();
        } catch (Exception e) {
            return -1;
        }
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
}
