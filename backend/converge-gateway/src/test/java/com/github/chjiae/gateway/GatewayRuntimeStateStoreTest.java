package com.github.chjiae.gateway;

import com.github.chjiae.contract.gateway.GatewayExecutionResourceRuntimePolicySnapshot;
import com.github.chjiae.gateway.config.GatewayRuntimeGovernanceConfig;
import com.github.chjiae.gateway.governance.GatewayRuntimeLeaseAcquireResult;
import com.github.chjiae.gateway.governance.GatewayRuntimeLeaseAcquireStatus;
import com.github.chjiae.gateway.governance.GatewayRuntimeLeaseOutcome;
import com.github.chjiae.gateway.governance.GatewayRuntimeStateStore;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 网关运行时 Redis 状态存储测试。
 * 覆盖原子 lease 获取、释放、失败熔断和半开探测，不访问 PostgreSQL 或快照 Redis 查询路径。
 */
class GatewayRuntimeStateStoreTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    private static final Vertx VERTX = Vertx.vertx();

    private GatewayRuntimeStateStore store;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
            store = null;
        }
        flushRedis();
    }

    @AfterAll
    static void closeVertx() {
        VERTX.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
    }

    @Test
    void lease_并发满时拒绝且完成成功后释放名额() {
        store = startStore();
        GatewayExecutionResourceRuntimePolicySnapshot policy = policy(1, 3, 10_000L, 500L, 700L);

        GatewayRuntimeLeaseAcquireResult first = acquire(policy, "lease-1");
        GatewayRuntimeLeaseAcquireResult second = acquire(policy, "lease-2");
        complete(policy, "lease-1", GatewayRuntimeLeaseOutcome.SUCCESS);
        GatewayRuntimeLeaseAcquireResult third = acquire(policy, "lease-3");

        assertEquals(GatewayRuntimeLeaseAcquireStatus.GRANTED, first.status());
        assertEquals(GatewayRuntimeLeaseAcquireStatus.CONCURRENCY_FULL, second.status());
        assertEquals(GatewayRuntimeLeaseAcquireStatus.GRANTED, third.status());
    }

    @Test
    void lease_连续失败打开熔断且冷却后只允许一个半开探测() throws InterruptedException {
        store = startStore();
        GatewayExecutionResourceRuntimePolicySnapshot policy = policy(0, 2, 10_000L, 250L, 700L);

        completeAfterAcquire(policy, "lease-1", GatewayRuntimeLeaseOutcome.UPSTREAM_TIMEOUT);
        completeAfterAcquire(policy, "lease-2", GatewayRuntimeLeaseOutcome.UPSTREAM_SERVER_FAILURE);
        GatewayRuntimeLeaseAcquireResult opened = acquire(policy, "lease-3");
        Thread.sleep(320L);
        GatewayRuntimeLeaseAcquireResult halfOpen = acquire(policy, "lease-4");
        GatewayRuntimeLeaseAcquireResult busy = acquire(policy, "lease-5");
        complete(policy, "lease-4", GatewayRuntimeLeaseOutcome.SUCCESS);
        GatewayRuntimeLeaseAcquireResult closed = acquire(policy, "lease-6");

        assertEquals(GatewayRuntimeLeaseAcquireStatus.CIRCUIT_OPEN, opened.status());
        assertEquals(GatewayRuntimeLeaseAcquireStatus.HALF_OPEN_GRANTED, halfOpen.status());
        assertEquals(GatewayRuntimeLeaseAcquireStatus.HALF_OPEN_BUSY, busy.status());
        assertEquals(GatewayRuntimeLeaseAcquireStatus.GRANTED, closed.status());
    }

    private GatewayRuntimeStateStore startStore() {
        GatewayRuntimeGovernanceConfig config = new GatewayRuntimeGovernanceConfig(
                50L, 1000L, 200L, 64);
        GatewayRuntimeStateStore stateStore = new GatewayRuntimeStateStore(VERTX, redisUri(), config);
        stateStore.start().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        return stateStore;
    }

    private GatewayRuntimeLeaseAcquireResult acquire(GatewayExecutionResourceRuntimePolicySnapshot policy,
                                                     String leaseId) {
        return store.acquire(policy, leaseId, System.currentTimeMillis())
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
    }

    private void completeAfterAcquire(GatewayExecutionResourceRuntimePolicySnapshot policy,
                                      String leaseId,
                                      GatewayRuntimeLeaseOutcome outcome) {
        assertEquals(GatewayRuntimeLeaseAcquireStatus.GRANTED, acquire(policy, leaseId).status());
        complete(policy, leaseId, outcome);
    }

    private void complete(GatewayExecutionResourceRuntimePolicySnapshot policy,
                          String leaseId,
                          GatewayRuntimeLeaseOutcome outcome) {
        store.complete(policy, leaseId, outcome, System.currentTimeMillis())
                .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
    }

    private GatewayExecutionResourceRuntimePolicySnapshot policy(int maxConcurrent,
                                                                 int failureThreshold,
                                                                 long failureResetAfterMs,
                                                                 long failureCooldownMs,
                                                                 long rateLimitCooldownMs) {
        return new GatewayExecutionResourceRuntimePolicySnapshot("tenant-1", "policy-1", "res-1",
                1L, maxConcurrent, failureThreshold, failureResetAfterMs,
                failureCooldownMs, rateLimitCooldownMs);
    }

    private static void flushRedis() {
        Redis redis = Redis.createClient(VERTX, new RedisOptions().setConnectionString(redisUri()));
        try {
            redis.connect()
                    .compose(connection -> connection.send(Request.cmd(Command.FLUSHDB))
                            .eventually(() -> connection.close()))
                    .toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        } finally {
            redis.close();
        }
    }

    private static String redisUri() {
        return "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
    }
}
