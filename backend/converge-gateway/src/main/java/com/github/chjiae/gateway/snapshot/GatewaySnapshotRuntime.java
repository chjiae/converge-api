package com.github.chjiae.gateway.snapshot;

import com.github.chjiae.contract.gateway.GatewayExecutionResourceSnapshot;
import com.github.chjiae.contract.gateway.GatewaySnapshotCrypto;
import com.github.chjiae.contract.gateway.GatewaySnapshotJson;
import com.github.chjiae.contract.gateway.GatewaySnapshotManifest;
import com.github.chjiae.contract.gateway.GatewaySnapshotRedisKeys;
import com.github.chjiae.contract.gateway.GatewaySnapshotSchema;
import com.github.chjiae.contract.gateway.GatewaySnapshotSyncState;
import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;
import com.github.chjiae.gateway.config.GatewaySnapshotConfig;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网关快照运行时。
 * 通过 Redis initial/periodic reconciliation 与 Pub/Sub 提示加载快照，
 * 校验通过后以 copy-on-write 方式原子替换本地不可变租户快照。
 */
public class GatewaySnapshotRuntime {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(GatewaySnapshotRuntime.class);

    /** Vert.x 实例 */
    private final Vertx vertx;

    /** 快照配置 */
    private final GatewaySnapshotConfig config;

    /** Redis client */
    private final Redis redis;

    /** 本地租户快照 */
    private final AtomicReference<Map<String, GatewayLoadedTenantSnapshot>> localSnapshots =
            new AtomicReference<>(Map.of());

    /** 命令连接 */
    private RedisConnection commandConnection;

    /** Pub/Sub 连接 */
    private RedisConnection subscriberConnection;

    /** 周期对账 timer ID */
    private long periodicTimerId = -1;

    /** 已完成首次成功对账 */
    private volatile boolean firstReconcileSucceeded;

    /** 当前 Redis 中 index 租户数量 */
    private volatile int indexTenantCount;

    /** 最近成功对账时间 */
    private volatile long lastSuccessfulReconcileEpochMillis;

    /** 最近 Redis 失败时间 */
    private volatile long lastRedisFailureEpochMillis;

    /** 当前是否存在损坏的 current 快照 */
    private volatile boolean currentSnapshotCorrupted;

    /** 最近错误分类 */
    private volatile String latestErrorCategory;

    /**
     * 创建网关快照运行时。
     *
     * @param vertx Vert.x 实例
     * @param config 快照配置
     */
    public GatewaySnapshotRuntime(Vertx vertx, GatewaySnapshotConfig config) {
        this.vertx = vertx;
        this.config = config;
        this.redis = Redis.createClient(vertx, new RedisOptions().setConnectionString(config.redisUri()));
    }

    /**
     * 启动快照运行时。
     *
     * @return 启动结果
     */
    public Future<Void> start() {
        reconcileAll("INITIAL");
        startSubscription();
        periodicTimerId = vertx.setPeriodic(config.reconcileIntervalMs(), ignored -> reconcileAll("PERIODIC"));
        return Future.succeededFuture();
    }

    /**
     * 关闭快照运行时。
     *
     * @return 关闭结果
     */
    public Future<Void> close() {
        if (periodicTimerId != -1) {
            vertx.cancelTimer(periodicTimerId);
        }
        List<Future<?>> futures = new ArrayList<>();
        if (subscriberConnection != null) {
            futures.add(subscriberConnection.close());
        }
        if (commandConnection != null) {
            futures.add(commandConnection.close());
        }
        redis.close();
        return Future.all(futures).mapEmpty();
    }

    /**
     * 获取安全运行时状态。
     *
     * @return 快照状态
     */
    public GatewaySnapshotRuntimeStatus status() {
        Map<String, GatewayLoadedTenantSnapshot> snapshotMap = localSnapshots.get();
        List<GatewaySnapshotRuntimeStatus.TenantRevision> tenants = snapshotMap.entrySet().stream()
                .map(entry -> new GatewaySnapshotRuntimeStatus.TenantRevision(entry.getKey(),
                        entry.getValue().snapshot().revision()))
                .sorted(java.util.Comparator.comparing(GatewaySnapshotRuntimeStatus.TenantRevision::tenantId))
                .toList();
        return new GatewaySnapshotRuntimeStatus(syncState(), indexTenantCount, snapshotMap.size(),
                lastSuccessfulReconcileEpochMillis, latestErrorCategory, tenants);
    }

    /**
     * 执行全量对账。
     *
     * @param reason 触发原因
     */
    private void reconcileAll(String reason) {
        smembers(GatewaySnapshotRedisKeys.tenantIndexKey())
                .compose(tenantIds -> {
                    indexTenantCount = tenantIds.size();
                    return loadAllTenants(tenantIds);
                })
                .onSuccess(ignored -> {
                    firstReconcileSucceeded = true;
                    currentSnapshotCorrupted = false;
                    latestErrorCategory = null;
                    lastSuccessfulReconcileEpochMillis = System.currentTimeMillis();
                    log.info("网关快照对账成功，原因: {}，index 租户数: {}，本地租户数: {}",
                            reason, indexTenantCount, localSnapshots.get().size());
                })
                .onFailure(throwable -> {
                    if (throwable instanceof GatewaySnapshotValidationException validationException) {
                        currentSnapshotCorrupted = true;
                        latestErrorCategory = validationException.category();
                    } else {
                        lastRedisFailureEpochMillis = System.currentTimeMillis();
                        latestErrorCategory = "REDIS_UNAVAILABLE";
                    }
                    log.warn("网关快照对账失败，原因: {}，错误分类: {}", reason, latestErrorCategory);
                });
    }

    /**
     * 加载所有租户快照。
     *
     * @param tenantIds 租户 ID 集合
     * @return 加载结果
     */
    private Future<Void> loadAllTenants(Set<String> tenantIds) {
        if (tenantIds.isEmpty()) {
            localSnapshots.set(Map.of());
            return Future.succeededFuture();
        }
        Promise<Void> promise = Promise.promise();
        Map<String, GatewayLoadedTenantSnapshot> current = localSnapshots.get();
        Map<String, GatewayLoadedTenantSnapshot> next = new HashMap<>();
        List<Throwable> failures = new ArrayList<>();
        loadTenantSequentially(new ArrayList<>(tenantIds), 0, current, next, failures, promise);
        return promise.future();
    }

    /**
     * 逐个加载租户，单个租户失败时保留旧版本，并继续尝试其他租户。
     */
    private void loadTenantSequentially(List<String> tenantIds, int index,
                                        Map<String, GatewayLoadedTenantSnapshot> current,
                                        Map<String, GatewayLoadedTenantSnapshot> next,
                                        List<Throwable> failures,
                                        Promise<Void> promise) {
        if (index >= tenantIds.size()) {
            localSnapshots.set(Map.copyOf(next));
            if (failures.isEmpty()) {
                promise.complete();
            } else {
                promise.fail(failures.getFirst());
            }
            return;
        }
        String tenantId = tenantIds.get(index);
        loadTenant(tenantId)
                .onSuccess(loaded -> {
                    next.put(tenantId, loaded);
                    loadTenantSequentially(tenantIds, index + 1, current, next, failures, promise);
                })
                .onFailure(throwable -> {
                    if (current.containsKey(tenantId)) {
                        next.put(tenantId, current.get(tenantId));
                    }
                    failures.add(throwable);
                    if (throwable instanceof GatewaySnapshotValidationException validationException) {
                        currentSnapshotCorrupted = true;
                        latestErrorCategory = validationException.category();
                    }
                    loadTenantSequentially(tenantIds, index + 1, current, next, failures, promise);
                });
    }

    /**
     * 加载单个租户快照。
     *
     * @param tenantId 租户 ID
     * @return 已加载快照
     */
    private Future<GatewayLoadedTenantSnapshot> loadTenant(String tenantId) {
        return get(GatewaySnapshotRedisKeys.currentManifestKey(tenantId))
                .compose(manifestJson -> {
                    if (manifestJson == null || manifestJson.isBlank()) {
                        return Future.failedFuture(new GatewaySnapshotValidationException("MANIFEST_MISSING",
                                "租户 current manifest 不存在"));
                    }
                    GatewaySnapshotManifest manifest = parseManifest(manifestJson);
                    validateManifest(manifest, tenantId);
                    return get(manifest.payloadRedisKey())
                            .map(payloadJson -> validatePayload(manifest, payloadJson));
                });
    }

    /**
     * 解析 Manifest。
     */
    private GatewaySnapshotManifest parseManifest(String manifestJson) {
        try {
            return GatewaySnapshotJson.fromJson(manifestJson, GatewaySnapshotManifest.class);
        } catch (Exception e) {
            throw new GatewaySnapshotValidationException("MANIFEST_JSON_INVALID", "Manifest JSON 不合法");
        }
    }

    /**
     * 验证 Manifest。
     */
    private void validateManifest(GatewaySnapshotManifest manifest, String expectedTenantId) {
        if (manifest.schemaVersion() != GatewaySnapshotSchema.CURRENT_VERSION) {
            throw new GatewaySnapshotValidationException("SCHEMA_UNSUPPORTED", "Manifest schema 不兼容");
        }
        if (!expectedTenantId.equals(manifest.tenantId())) {
            throw new GatewaySnapshotValidationException("TENANT_REVISION_MISMATCH", "Manifest 租户不匹配");
        }
        if (!config.keyId().equals(manifest.gatewayKeyId())) {
            throw new GatewaySnapshotValidationException("MANIFEST_KEY_ID_MISMATCH", "Manifest keyId 不匹配");
        }
        if (!GatewaySnapshotCrypto.verifyManifest(manifest, config.signingKeyBytes())) {
            throw new GatewaySnapshotValidationException("MANIFEST_HMAC_INVALID", "Manifest HMAC 不匹配");
        }
    }

    /**
     * 验证 payload 并解封装秘密。
     */
    private GatewayLoadedTenantSnapshot validatePayload(GatewaySnapshotManifest manifest, String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new GatewaySnapshotValidationException("PAYLOAD_MISSING", "Payload 不存在");
        }
        byte[] payloadBytes = payloadJson.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(GatewaySnapshotCrypto.sha256Hex(payloadBytes).getBytes(StandardCharsets.UTF_8),
                manifest.payloadSha256Hex().getBytes(StandardCharsets.UTF_8))) {
            throw new GatewaySnapshotValidationException("PAYLOAD_SHA_INVALID", "Payload SHA 不匹配");
        }
        GatewayTenantSnapshot snapshot;
        try {
            snapshot = GatewaySnapshotJson.fromJson(payloadJson, GatewayTenantSnapshot.class);
        } catch (Exception e) {
            throw new GatewaySnapshotValidationException("PAYLOAD_JSON_INVALID", "Payload JSON 不合法");
        }
        if (snapshot.schemaVersion() != GatewaySnapshotSchema.CURRENT_VERSION
                || !manifest.tenantId().equals(snapshot.tenantId())
                || manifest.revision() != snapshot.revision()) {
            throw new GatewaySnapshotValidationException("TENANT_REVISION_MISMATCH", "Payload 与 Manifest 不匹配");
        }
        Map<String, String> secrets = new HashMap<>();
        for (GatewayExecutionResourceSnapshot resource : snapshot.executionResources()) {
            try {
                String secret = GatewaySnapshotCrypto.decryptSecret(resource.secretEnvelope(),
                        config.encryptionKeyBytes(),
                        GatewaySnapshotCrypto.secretAad(snapshot.schemaVersion(), snapshot.tenantId(),
                                resource.resourceId(), resource.credentialId(), snapshot.revision(), config.keyId()),
                        config.keyId());
                secrets.put(resource.resourceId(), secret);
            } catch (Exception e) {
                throw new GatewaySnapshotValidationException("SECRET_DECRYPT_FAILED", "秘密 envelope 解封装失败");
            }
        }
        return new GatewayLoadedTenantSnapshot(snapshot, secrets);
    }

    /**
     * 启动 Pub/Sub 订阅。
     */
    private void startSubscription() {
        redis.connect()
                .onSuccess(connection -> {
                    subscriberConnection = connection;
                    connection.handler(response -> reconcileAll("PUBSUB"));
                    connection.exceptionHandler(throwable -> scheduleSubscriptionReconnect());
                    connection.endHandler(ignored -> scheduleSubscriptionReconnect());
                    connection.send(Request.cmd(Command.SUBSCRIBE).arg(GatewaySnapshotRedisKeys.changedChannel()));
                })
                .onFailure(throwable -> {
                    latestErrorCategory = "REDIS_UNAVAILABLE";
                    scheduleSubscriptionReconnect();
                });
    }

    /**
     * 调度 Pub/Sub 重连。
     */
    private void scheduleSubscriptionReconnect() {
        subscriberConnection = null;
        vertx.setTimer(500, ignored -> startSubscription());
    }

    /**
     * 获取字符串 key。
     */
    private Future<String> get(String key) {
        return send(Command.GET, key).map(response -> response == null ? null : response.toString());
    }

    /**
     * 读取 Set 成员。
     */
    private Future<Set<String>> smembers(String key) {
        return send(Command.SMEMBERS, key).map(response -> {
            Set<String> values = new LinkedHashSet<>();
            if (response != null) {
                for (Response item : response) {
                    values.add(item.toString());
                }
            }
            return values;
        });
    }

    /**
     * 发送 Redis 命令。
     */
    private Future<Response> send(Command command, String... args) {
        return ensureCommandConnection().compose(connection -> {
            Request request = Request.cmd(command);
            for (String arg : args) {
                request.arg(arg);
            }
            return connection.send(request);
        });
    }

    /**
     * 确保命令连接可用。
     */
    private Future<RedisConnection> ensureCommandConnection() {
        if (commandConnection != null) {
            return Future.succeededFuture(commandConnection);
        }
        Promise<RedisConnection> promise = Promise.promise();
        redis.connect()
                .onSuccess(connection -> {
                    commandConnection = connection;
                    connection.exceptionHandler(throwable -> commandConnection = null);
                    connection.endHandler(ignored -> commandConnection = null);
                    promise.complete(connection);
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    /**
     * 计算当前同步状态。
     */
    private GatewaySnapshotSyncState syncState() {
        if (!firstReconcileSucceeded || currentSnapshotCorrupted) {
            return GatewaySnapshotSyncState.NOT_READY;
        }
        long now = System.currentTimeMillis();
        if (lastRedisFailureEpochMillis > lastSuccessfulReconcileEpochMillis) {
            if (!localSnapshots.get().isEmpty() && now - lastSuccessfulReconcileEpochMillis <= config.maxStalenessMs()) {
                return GatewaySnapshotSyncState.DEGRADED;
            }
            return GatewaySnapshotSyncState.NOT_READY;
        }
        if (now - lastSuccessfulReconcileEpochMillis > config.maxStalenessMs()) {
            return GatewaySnapshotSyncState.NOT_READY;
        }
        return GatewaySnapshotSyncState.READY;
    }
}
