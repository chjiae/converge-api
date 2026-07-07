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
 * 网关 Client API Key 数据面访问测试。
 * 覆盖 V3 全局 key index、认证失败、授权模型列表、空授权结果与 last-known-good。
 */
class GatewayClientAccessRuntimeTest {

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
    void models_按ClientKey认证并只返回授权且可路由模型() throws Exception {
        GatewayClientKeyCrypto.GeneratedClientKey keyA = GatewayClientKeyCrypto.generate();
        GatewayClientKeyCrypto.GeneratedClientKey keyB = GatewayClientKeyCrypto.generate();
        GatewayClientKeyCrypto.GeneratedClientKey emptyKey = GatewayClientKeyCrypto.generate();
        publish(v3Snapshot("tenant-a", 1, keyA, true, true, true));
        publish(v3Snapshot("tenant-b", 1, keyB, true, true, true));
        publish(v3Snapshot("tenant-empty", 1, emptyKey, true, true, false));

        runtime = GatewayRuntime.start(gatewayConfig(redisUri()), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        waitUntil(() -> snapshotStatus().contains("\"clientKeyCount\":3"), 3000);

        HttpResponse<String> missing = get("/v1/models", null);
        assertEquals(401, missing.statusCode());
        assertTrue(missing.body().contains("\"code\":\"invalid_api_key\""));

        HttpResponse<String> wrong = get("/v1/models", mutateLastCharacter(keyA.rawKey()));
        assertEquals(401, wrong.statusCode());
        assertTrue(wrong.body().contains("\"code\":\"invalid_api_key\""));

        HttpResponse<String> authorizedA = get("/v1/models", keyA.rawKey());
        assertEquals(200, authorizedA.statusCode());
        assertTrue(authorizedA.body().contains("\"object\":\"list\""));
        assertTrue(authorizedA.body().contains("\"id\":\"public-chat-tenant-a\""));
        assertFalse(authorizedA.body().contains("tenant-b"));
        assertFalse(authorizedA.body().contains("upstream-chat"));

        HttpResponse<String> authorizedB = get("/v1/models", keyB.rawKey());
        assertEquals(200, authorizedB.statusCode());
        assertTrue(authorizedB.body().contains("\"id\":\"public-chat-tenant-b\""));
        assertFalse(authorizedB.body().contains("tenant-a"));

        HttpResponse<String> empty = get("/v1/models", emptyKey.rawKey());
        assertEquals(200, empty.statusCode());
        assertTrue(empty.body().contains("\"data\":[]"));
    }

    @Test
    void models_无授权返回403且未就绪返回503() throws Exception {
        GatewayClientKeyCrypto.GeneratedClientKey noGrantKey = GatewayClientKeyCrypto.generate();
        publish(v3Snapshot("tenant-no-grant", 1, noGrantKey, true, false, true));

        runtime = GatewayRuntime.start(gatewayConfig(redisUri()), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        waitUntil(() -> snapshotStatus().contains("\"clientKeyCount\":1"), 3000);

        HttpResponse<String> denied = get("/v1/models", noGrantKey.rawKey());
        assertEquals(403, denied.statusCode());
        assertTrue(denied.body().contains("\"code\":\"access_denied\""));

        runtime.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        runtime = GatewayRuntime.start(gatewayConfig("redis://127.0.0.1:1"), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        HttpResponse<String> notReady = get("/v1/models", noGrantKey.rawKey());
        assertEquals(503, notReady.statusCode());
        assertTrue(notReady.body().contains("\"code\":\"gateway_not_ready\""));
    }

    @Test
    void runtime_非法V3保留旧KeyIndex和RoutePlan() throws Exception {
        GatewayClientKeyCrypto.GeneratedClientKey key = GatewayClientKeyCrypto.generate();
        publish(v3Snapshot("tenant-a", 1, key, true, true, true));

        runtime = GatewayRuntime.start(gatewayConfig(redisUri()), true)
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        waitUntil(() -> snapshotStatus().contains("\"clientKeyCount\":1"), 3000);
        assertEquals(200, get("/v1/models", key.rawKey()).statusCode());

        publish(v3Snapshot("tenant-a", 2, key, false, true, true));
        waitUntil(() -> snapshotStatus().contains("STATIC_ROUTE_INVALID")
                && snapshotStatus().contains("\"revision\":1"), 3000);

        HttpResponse<String> afterInvalid = get("/v1/models", key.rawKey());
        assertEquals(200, afterInvalid.statusCode());
        assertTrue(afterInvalid.body().contains("\"id\":\"public-chat-tenant-a\""));
    }

    private GatewayTenantSnapshot v3Snapshot(String tenantId, long revision,
                                             GatewayClientKeyCrypto.GeneratedClientKey key,
                                             boolean validTopology,
                                             boolean includeGrant,
                                             boolean grantHasRoutePlan) {
        GatewaySecretEnvelope envelope = GatewaySnapshotCrypto.encryptSecret("runtime-secret",
                SNAPSHOT_ENCRYPTION_KEY,
                GatewaySnapshotCrypto.secretAad(GatewaySnapshotSchema.VERSION_3, tenantId, "res-1",
                        "cred-1", revision, "gateway-test-key"),
                "gateway-test-key");
        byte[] salt = GatewayClientKeyCrypto.generateSalt();
        byte[] verifier = GatewayClientKeyCrypto.verifier(key.rawKey(), key.keyId(), 1, salt);
        List<GatewayAccessGroupModelGrantSnapshot> grants = includeGrant
                ? List.of(new GatewayAccessGroupModelGrantSnapshot(tenantId, "grant-1", "group-1", "model-1",
                "public-chat-" + tenantId, "CHAT_COMPLETIONS", "ENABLED"))
                : List.of();
        List<GatewayClientApiKeyAccessGroupSnapshot> keyGroups = includeGrant || !grantHasRoutePlan
                ? List.of(new GatewayClientApiKeyAccessGroupSnapshot(tenantId, "binding-1", "key-1", "group-1", "ENABLED"))
                : List.of();

        return new GatewayTenantSnapshot(GatewaySnapshotSchema.VERSION_3, tenantId, revision, System.currentTimeMillis(),
                List.of(new GatewayPublicModelSnapshot(tenantId, "model-1", "public-chat-" + tenantId,
                        "公开模型", "chat")),
                List.of(new GatewayExecutionResourceSnapshot(tenantId, "res-1", "provider-1", "conn-1",
                        "cred-1", "DIRECT_API", "ENABLED", "OPENAI", "OPENAI_COMPATIBLE",
                        "https://api.example.test/v1/", envelope)),
                List.of(new GatewayResourcePoolSnapshot(tenantId, "pool-1", "primary", "主池",
                        "ENABLED", "PRIORITY_WEIGHTED", List.of(
                        new GatewayResourcePoolMemberSnapshot(tenantId, "pool-1", "res-1",
                                "ENABLED", 100, 100)))),
                validTopology && grantHasRoutePlan
                        ? List.of(new GatewayResourceModelBindingSnapshot(tenantId, "res-1", "model-1",
                        "CHAT_COMPLETIONS", "upstream-chat", "ENABLED"))
                        : List.of(),
                grantHasRoutePlan
                        ? List.of(new GatewayRoutePolicySnapshot(tenantId, "policy-1", "model-1",
                        "public-chat-" + tenantId, "CHAT_COMPLETIONS", "ENABLED", "PRIORITY_WEIGHTED", List.of(
                        new GatewayRouteTargetSnapshot(tenantId, "policy-1", "pool-1",
                                "ENABLED", 100, 100))))
                        : List.of(),
                List.of(new GatewayAccessGroupSnapshot(tenantId, "group-1", "default", "ENABLED")),
                grants,
                List.of(new GatewayClientApiKeySnapshot(tenantId, "key-1", key.keyId(),
                        "ENABLED", "SHA-256",
                        Base64.getEncoder().encodeToString(salt),
                        Base64.getEncoder().encodeToString(verifier),
                        1, System.currentTimeMillis() + 3_600_000L)),
                keyGroups);
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

    private GatewayConfig gatewayConfig(String redisUri) {
        GatewaySnapshotConfig snapshotConfig = new GatewaySnapshotConfig(
                redisUri,
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

    private String mutateLastCharacter(String rawKey) {
        char last = rawKey.charAt(rawKey.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        return rawKey.substring(0, rawKey.length() - 1) + replacement;
    }

    private HttpResponse<String> get(String path, String rawKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + runtime.actualPort() + path))
                .timeout(Duration.ofSeconds(3))
                .GET();
        if (rawKey != null) {
            builder.header("Authorization", "Bearer " + rawKey);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String snapshotStatus() {
        try {
            return get("/internal/snapshot-status", null).body();
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
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, value);
        return key;
    }
}
