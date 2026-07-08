package com.github.chjiae.gateway.governance;

import com.github.chjiae.gateway.config.GatewayRuntimeGovernanceConfig;
import com.github.chjiae.gateway.snapshot.GatewayOpenAiExecutionTarget;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关运行时治理运行时。
 * 负责按静态候选顺序获取 Redis lease，并在请求结束时上报安全结果分类。
 */
public class GatewayRuntimeGovernanceRuntime {

    /** Vert.x 实例 */
    private final Vertx vertx;

    /** 运行时治理配置 */
    private final GatewayRuntimeGovernanceConfig config;

    /** Redis 状态存储 */
    private final GatewayRuntimeStateStore stateStore;

    /** 安全随机数 */
    private final SecureRandom secureRandom = new SecureRandom();

    /** 本地活跃 lease */
    private final Map<String, GatewayRuntimeLease> activeLeases = new ConcurrentHashMap<>();

    /** 是否排空关闭 */
    private final AtomicBoolean draining = new AtomicBoolean(false);

    /** Redis 可用状态 */
    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);

    /** 状态存储是否已启动 */
    private final AtomicBoolean storeStarted = new AtomicBoolean(false);

    /** 状态存储是否正在重连 */
    private final AtomicBoolean storeStarting = new AtomicBoolean(false);

    /** lease 获取成功计数 */
    private final AtomicLong acquireGrantedCount = new AtomicLong();

    /** lease 获取拒绝计数 */
    private final AtomicLong acquireRejectedCount = new AtomicLong();

    /** 续租失败计数 */
    private final AtomicLong renewFailureCount = new AtomicLong();

    /** Redis 运行时状态不可用计数 */
    private final AtomicLong runtimeStateUnavailableCount = new AtomicLong();

    /**
     * 创建运行时治理运行时。
     *
     * @param vertx Vert.x 实例
     * @param redisUri Redis URI
     * @param config 运行时治理配置
     */
    public GatewayRuntimeGovernanceRuntime(Vertx vertx, String redisUri, GatewayRuntimeGovernanceConfig config) {
        this.vertx = vertx;
        this.config = config;
        this.stateStore = new GatewayRuntimeStateStore(vertx, redisUri, config);
    }

    /**
     * 启动运行时治理。
     *
     * @return 启动结果
     */
    public Future<Void> start() {
        return stateStore.start()
                .onSuccess(ignored -> {
                    storeStarted.set(true);
                    redisAvailable.set(true);
                })
                .onFailure(ignored -> {
                    storeStarted.set(false);
                    redisAvailable.set(false);
                })
                .recover(throwable -> Future.succeededFuture());
    }

    /**
     * 获取运行时治理配置。
     *
     * @return 运行时治理配置
     */
    public GatewayRuntimeGovernanceConfig config() {
        return config;
    }

    /**
     * 进入排空状态。
     */
    public void beginDrain() {
        draining.set(true);
    }

    /**
     * 按候选顺序获取第一个可用 lease。
     *
     * @param targets 执行目标候选
     * @return 获取结果
     */
    public Future<GatewayRuntimeAcquireResult> acquire(List<GatewayOpenAiExecutionTarget> targets) {
        if (draining.get()) {
            return Future.succeededFuture(GatewayRuntimeAcquireResult.rejected(
                    GatewayRuntimeLeaseAcquireStatus.RUNTIME_STATE_UNAVAILABLE));
        }
        if (targets == null || targets.isEmpty()) {
            acquireRejectedCount.incrementAndGet();
            return Future.succeededFuture(GatewayRuntimeAcquireResult.rejected(
                    GatewayRuntimeLeaseAcquireStatus.CONCURRENCY_FULL));
        }
        if (!storeStarted.get()) {
            return ensureStoreStarted()
                    .compose(started -> {
                        if (!started) {
                            runtimeStateUnavailableCount.incrementAndGet();
                            return Future.succeededFuture(GatewayRuntimeAcquireResult.rejected(
                                    GatewayRuntimeLeaseAcquireStatus.RUNTIME_STATE_UNAVAILABLE));
                        }
                        int maxAttempts = Math.min(config.maxCandidateAttempts(), targets.size());
                        return acquireSequential(targets, 0, maxAttempts);
                    });
        }
        int maxAttempts = Math.min(config.maxCandidateAttempts(), targets.size());
        return acquireSequential(targets, 0, maxAttempts);
    }

    private Future<Boolean> ensureStoreStarted() {
        if (storeStarted.get()) {
            return Future.succeededFuture(true);
        }
        if (!storeStarting.compareAndSet(false, true)) {
            return Future.succeededFuture(false);
        }
        return stateStore.start()
                .map(ignored -> {
                    storeStarted.set(true);
                    redisAvailable.set(true);
                    return true;
                })
                .recover(throwable -> {
                    storeStarted.set(false);
                    redisAvailable.set(false);
                    return Future.succeededFuture(false);
                })
                .andThen(ignored -> storeStarting.set(false));
    }

    private Future<GatewayRuntimeAcquireResult> acquireSequential(List<GatewayOpenAiExecutionTarget> targets,
                                                                  int index,
                                                                  int maxAttempts) {
        if (index >= maxAttempts) {
            acquireRejectedCount.incrementAndGet();
            return Future.succeededFuture(GatewayRuntimeAcquireResult.rejected(
                    GatewayRuntimeLeaseAcquireStatus.CONCURRENCY_FULL));
        }
        GatewayOpenAiExecutionTarget target = targets.get(index);
        String leaseId = generateLeaseId();
        return stateStore.acquire(target.runtimePolicy(), leaseId, System.currentTimeMillis())
                .compose(result -> {
                    if (result.status() == GatewayRuntimeLeaseAcquireStatus.GRANTED
                            || result.status() == GatewayRuntimeLeaseAcquireStatus.HALF_OPEN_GRANTED) {
                        redisAvailable.set(true);
                        GatewayRuntimeLease lease = new GatewayRuntimeLease(target, target.runtimePolicy(),
                                result.leaseId(), result.halfOpen());
                        activeLeases.put(result.leaseId(), lease);
                        acquireGrantedCount.incrementAndGet();
                        return Future.succeededFuture(GatewayRuntimeAcquireResult.granted(lease));
                    }
                    if (result.status() == GatewayRuntimeLeaseAcquireStatus.RUNTIME_STATE_UNAVAILABLE) {
                        redisAvailable.set(false);
                        runtimeStateUnavailableCount.incrementAndGet();
                        return Future.succeededFuture(GatewayRuntimeAcquireResult.rejected(result.status()));
                    }
                    acquireRejectedCount.incrementAndGet();
                    return acquireSequential(targets, index + 1, maxAttempts);
                })
                .recover(throwable -> {
                    redisAvailable.set(false);
                    runtimeStateUnavailableCount.incrementAndGet();
                    return Future.succeededFuture(GatewayRuntimeAcquireResult.rejected(
                            GatewayRuntimeLeaseAcquireStatus.RUNTIME_STATE_UNAVAILABLE));
                });
    }

    /**
     * 上报 lease 完成结果。
     *
     * @param lease lease
     * @param outcome 完成分类
     * @return 完成结果
     */
    public Future<Void> complete(GatewayRuntimeLease lease, GatewayRuntimeLeaseOutcome outcome) {
        if (lease == null || activeLeases.remove(lease.leaseId()) == null) {
            return Future.succeededFuture();
        }
        return stateStore.complete(lease.policy(), lease.leaseId(), outcome, System.currentTimeMillis())
                .onSuccess(ignored -> redisAvailable.set(true))
                .onFailure(ignored -> redisAvailable.set(false))
                .recover(throwable -> Future.succeededFuture());
    }

    /**
     * 启动周期续租。
     *
     * @param lease lease
     * @param onLost 续租失败回调
     * @return timer ID
     */
    public long startRenewing(GatewayRuntimeLease lease, Runnable onLost) {
        return vertx.setPeriodic(config.leaseRenewIntervalMs(), ignored ->
                stateStore.renew(lease.policy(), lease.leaseId(), System.currentTimeMillis())
                        .onSuccess(renewed -> {
                            redisAvailable.set(true);
                            if (!renewed) {
                                renewFailureCount.incrementAndGet();
                                activeLeases.remove(lease.leaseId());
                                onLost.run();
                            }
                        })
                        .onFailure(throwable -> {
                            redisAvailable.set(false);
                            renewFailureCount.incrementAndGet();
                            activeLeases.remove(lease.leaseId());
                            onLost.run();
                        }));
    }

    /**
     * 取消续租 timer。
     *
     * @param timerId timer ID
     */
    public void cancelRenewing(long timerId) {
        if (timerId >= 0) {
            vertx.cancelTimer(timerId);
        }
    }

    /**
     * 获取安全状态摘要。
     *
     * @return 状态摘要
     */
    public GatewayRuntimeGovernanceStatus status() {
        String state = draining.get() ? "DRAINING" : "RUNNING";
        return new GatewayRuntimeGovernanceStatus(state, redisAvailable.get(), activeLeases.size(),
                acquireGrantedCount.get(), acquireRejectedCount.get(), renewFailureCount.get(),
                runtimeStateUnavailableCount.get());
    }

    /**
     * 关闭运行时治理。
     *
     * @return 关闭结果
     */
    public Future<Void> close() {
        beginDrain();
        List<GatewayRuntimeLease> leases = List.copyOf(activeLeases.values());
        Future<Void> completeFuture = Future.succeededFuture();
        for (GatewayRuntimeLease lease : leases) {
            completeFuture = completeFuture.compose(ignored -> complete(lease, GatewayRuntimeLeaseOutcome.GATEWAY_SHUTDOWN));
        }
        return completeFuture.compose(ignored -> stateStore.close());
    }

    private String generateLeaseId() {
        byte[] random = new byte[18];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
}
