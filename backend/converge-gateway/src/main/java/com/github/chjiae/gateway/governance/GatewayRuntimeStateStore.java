package com.github.chjiae.gateway.governance;

import com.github.chjiae.contract.gateway.GatewayExecutionResourceRuntimePolicySnapshot;
import com.github.chjiae.gateway.config.GatewayRuntimeGovernanceConfig;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.Response;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网关运行时 Redis 状态存储。
 * 所有 lease、熔断和冷却状态均通过固定 Lua 脚本原子读写，禁止 Java 侧 GET+INCR 组合状态。
 */
public class GatewayRuntimeStateStore {

    /** acquire Lua 脚本 */
    private static final String ACQUIRE_SCRIPT = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local state = redis.call('HGET', KEYS[2], 'state')
            if not state or state == '' then
                state = 'CLOSED'
            end
            local open_until = tonumber(redis.call('HGET', KEYS[2], 'openUntilEpochMillis') or '0')
            local lease_id = ARGV[2]
            local lease_expire_at = tonumber(ARGV[3])
            local max_concurrent = tonumber(ARGV[4])
            local key_ttl_ms = tonumber(ARGV[5])
            if state == 'OPEN' then
                if open_until > tonumber(ARGV[1]) then
                    redis.call('PEXPIRE', KEYS[1], key_ttl_ms)
                    redis.call('PEXPIRE', KEYS[2], key_ttl_ms)
                    return {'CIRCUIT_OPEN'}
                end
                redis.call('ZADD', KEYS[1], lease_expire_at, lease_id)
                redis.call('HSET', KEYS[2], 'state', 'HALF_OPEN', 'halfOpenLeaseId', lease_id)
                redis.call('PEXPIRE', KEYS[1], key_ttl_ms)
                redis.call('PEXPIRE', KEYS[2], key_ttl_ms)
                return {'HALF_OPEN_GRANTED', lease_id}
            end
            if state == 'HALF_OPEN' then
                redis.call('PEXPIRE', KEYS[1], key_ttl_ms)
                redis.call('PEXPIRE', KEYS[2], key_ttl_ms)
                return {'HALF_OPEN_BUSY'}
            end
            local current_count = redis.call('ZCARD', KEYS[1])
            if max_concurrent > 0 and current_count >= max_concurrent then
                redis.call('PEXPIRE', KEYS[1], key_ttl_ms)
                redis.call('PEXPIRE', KEYS[2], key_ttl_ms)
                return {'CONCURRENCY_FULL'}
            end
            redis.call('ZADD', KEYS[1], lease_expire_at, lease_id)
            redis.call('HSETNX', KEYS[2], 'state', 'CLOSED')
            redis.call('PEXPIRE', KEYS[1], key_ttl_ms)
            redis.call('PEXPIRE', KEYS[2], key_ttl_ms)
            return {'GRANTED', lease_id}
            """;

    /** renew Lua 脚本 */
    private static final String RENEW_SCRIPT = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local lease_id = ARGV[2]
            local score = redis.call('ZSCORE', KEYS[1], lease_id)
            if not score then
                return {'LEASE_LOST'}
            end
            local state = redis.call('HGET', KEYS[2], 'state')
            local half_open_lease_id = redis.call('HGET', KEYS[2], 'halfOpenLeaseId')
            if state == 'HALF_OPEN' and half_open_lease_id ~= lease_id then
                return {'LEASE_LOST'}
            end
            redis.call('ZADD', KEYS[1], ARGV[3], lease_id)
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('PEXPIRE', KEYS[2], ARGV[4])
            return {'RENEWED'}
            """;

    /** complete Lua 脚本 */
    private static final String COMPLETE_SCRIPT = """
            redis.call('ZREM', KEYS[1], ARGV[2])
            local now_ms = tonumber(ARGV[1])
            local lease_id = ARGV[2]
            local outcome = ARGV[3]
            local threshold = tonumber(ARGV[4])
            local failure_reset_after_ms = tonumber(ARGV[5])
            local failure_cooldown_ms = tonumber(ARGV[6])
            local rate_limit_cooldown_ms = tonumber(ARGV[7])
            local key_ttl_ms = tonumber(ARGV[8])
            local state = redis.call('HGET', KEYS[2], 'state')
            local half_open_lease_id = redis.call('HGET', KEYS[2], 'halfOpenLeaseId')
            local function close_circuit()
                redis.call('HSET', KEYS[2],
                    'state', 'CLOSED',
                    'consecutiveFailureCount', '0',
                    'lastFailureAtEpochMillis', '0',
                    'openUntilEpochMillis', '0',
                    'halfOpenLeaseId', '')
            end
            local function open_circuit(open_until)
                redis.call('HSET', KEYS[2],
                    'state', 'OPEN',
                    'openUntilEpochMillis', tostring(open_until),
                    'halfOpenLeaseId', '')
            end
            if outcome == 'SUCCESS' or outcome == 'REACHABLE_CLIENT_REJECTION' then
                close_circuit()
            elseif outcome == 'UPSTREAM_RATE_LIMITED' then
                redis.call('HSET', KEYS[2], 'consecutiveFailureCount', '0',
                    'lastFailureAtEpochMillis', tostring(now_ms))
                open_circuit(now_ms + rate_limit_cooldown_ms)
            elseif outcome == 'UPSTREAM_AUTH_FAILURE'
                or outcome == 'UPSTREAM_CONNECTION_FAILURE'
                or outcome == 'UPSTREAM_TIMEOUT'
                or outcome == 'UPSTREAM_SERVER_FAILURE'
                or outcome == 'UPSTREAM_PROTOCOL_FAILURE' then
                if state == 'HALF_OPEN' and half_open_lease_id == lease_id then
                    redis.call('HSET', KEYS[2], 'consecutiveFailureCount', tostring(threshold),
                        'lastFailureAtEpochMillis', tostring(now_ms))
                    open_circuit(now_ms + failure_cooldown_ms)
                else
                    local last_failure_at = tonumber(redis.call('HGET', KEYS[2], 'lastFailureAtEpochMillis') or '0')
                    local count = tonumber(redis.call('HGET', KEYS[2], 'consecutiveFailureCount') or '0')
                    if last_failure_at == 0 or now_ms - last_failure_at > failure_reset_after_ms then
                        count = 1
                    else
                        count = count + 1
                    end
                    redis.call('HSET', KEYS[2], 'consecutiveFailureCount', tostring(count),
                        'lastFailureAtEpochMillis', tostring(now_ms))
                    if count >= threshold then
                        open_circuit(now_ms + failure_cooldown_ms)
                    else
                        redis.call('HSET', KEYS[2], 'state', 'CLOSED', 'halfOpenLeaseId', '')
                    end
                end
            elseif state == 'HALF_OPEN' and half_open_lease_id == lease_id then
                open_circuit(now_ms + failure_cooldown_ms)
            end
            redis.call('PEXPIRE', KEYS[1], key_ttl_ms)
            redis.call('PEXPIRE', KEYS[2], key_ttl_ms)
            return {'COMPLETED'}
            """;

    /** Vert.x 实例 */
    private final Vertx vertx;

    /** Redis URI */
    private final String redisUri;

    /** 运行时治理配置 */
    private final GatewayRuntimeGovernanceConfig config;

    /** Redis client */
    private final Redis redis;

    /** Redis 命令连接 */
    private RedisConnection connection;

    /** acquire 脚本 SHA */
    private String acquireSha;

    /** renew 脚本 SHA */
    private String renewSha;

    /** complete 脚本 SHA */
    private String completeSha;

    /**
     * 创建 Redis 状态存储。
     *
     * @param vertx Vert.x 实例
     * @param redisUri Redis URI
     * @param config 运行时治理配置
     */
    public GatewayRuntimeStateStore(Vertx vertx, String redisUri, GatewayRuntimeGovernanceConfig config) {
        this.vertx = vertx;
        this.redisUri = redisUri;
        this.config = config;
        this.redis = Redis.createClient(vertx, new RedisOptions().setConnectionString(redisUri));
    }

    /**
     * 启动状态存储并加载 Lua 脚本。
     *
     * @return 启动结果
     */
    public Future<Void> start() {
        return redis.connect()
                .compose(redisConnection -> {
                    connection = redisConnection;
                    return reloadScripts();
                });
    }

    /**
     * 关闭 Redis 连接。
     *
     * @return 关闭结果
     */
    public Future<Void> close() {
        Future<Void> closeConnection = connection == null ? Future.succeededFuture() : connection.close();
        redis.close();
        return closeConnection;
    }

    /**
     * 获取运行时 lease。
     *
     * @param policy 运行时策略
     * @param leaseId lease ID
     * @param nowEpochMillis 当前时间，Unix 毫秒
     * @return 获取结果
     */
    public Future<GatewayRuntimeLeaseAcquireResult> acquire(GatewayExecutionResourceRuntimePolicySnapshot policy,
                                                            String leaseId,
                                                            long nowEpochMillis) {
        List<String> args = List.of(String.valueOf(nowEpochMillis),
                leaseId,
                String.valueOf(nowEpochMillis + config.leaseTtlMs()),
                String.valueOf(policy.maxConcurrentRequests()),
                String.valueOf(keyTtlMs(policy)));
        return executeScript(ScriptKind.ACQUIRE, policy, args)
                .map(this::toAcquireResult)
                .recover(throwable -> Future.succeededFuture(GatewayRuntimeLeaseAcquireResult.rejected(
                        GatewayRuntimeLeaseAcquireStatus.RUNTIME_STATE_UNAVAILABLE)));
    }

    /**
     * 续租运行时 lease。
     *
     * @param policy 运行时策略
     * @param leaseId lease ID
     * @param nowEpochMillis 当前时间，Unix 毫秒
     * @return true 表示续租成功
     */
    public Future<Boolean> renew(GatewayExecutionResourceRuntimePolicySnapshot policy,
                                 String leaseId,
                                 long nowEpochMillis) {
        List<String> args = List.of(String.valueOf(nowEpochMillis),
                leaseId,
                String.valueOf(nowEpochMillis + config.leaseTtlMs()),
                String.valueOf(keyTtlMs(policy)));
        return executeScript(ScriptKind.RENEW, policy, args)
                .map(response -> "RENEWED".equals(statusOf(response)));
    }

    /**
     * 完成运行时 lease 并更新健康状态。
     *
     * @param policy 运行时策略
     * @param leaseId lease ID
     * @param outcome 完成分类
     * @param nowEpochMillis 当前时间，Unix 毫秒
     * @return 完成结果
     */
    public Future<Void> complete(GatewayExecutionResourceRuntimePolicySnapshot policy,
                                 String leaseId,
                                 GatewayRuntimeLeaseOutcome outcome,
                                 long nowEpochMillis) {
        List<String> args = List.of(String.valueOf(nowEpochMillis),
                leaseId,
                outcome.name(),
                String.valueOf(policy.consecutiveFailureThreshold()),
                String.valueOf(policy.failureResetAfterMs()),
                String.valueOf(policy.failureCooldownMs()),
                String.valueOf(policy.rateLimitCooldownMs()),
                String.valueOf(keyTtlMs(policy)));
        return executeScript(ScriptKind.COMPLETE, policy, args).mapEmpty();
    }

    private Future<Void> reloadScripts() {
        return loadScript(ACQUIRE_SCRIPT)
                .compose(sha -> {
                    acquireSha = sha;
                    return loadScript(RENEW_SCRIPT);
                })
                .compose(sha -> {
                    renewSha = sha;
                    return loadScript(COMPLETE_SCRIPT);
                })
                .map(sha -> {
                    completeSha = sha;
                    return null;
                });
    }

    private Future<String> loadScript(String script) {
        Request request = Request.cmd(Command.SCRIPT).arg("LOAD").arg(script);
        return sendWithTimeout(request).map(Response::toString);
    }

    private Future<Response> executeScript(ScriptKind kind,
                                           GatewayExecutionResourceRuntimePolicySnapshot policy,
                                           List<String> args) {
        return sendWithTimeout(scriptRequest(shaFor(kind), policy, args))
                .recover(throwable -> {
                    if (!isNoScript(throwable)) {
                        return Future.failedFuture(throwable);
                    }
                    return reloadScripts()
                            .compose(ignored -> sendWithTimeout(scriptRequest(shaFor(kind), policy, args)));
                });
    }

    private String shaFor(ScriptKind kind) {
        return switch (kind) {
            case ACQUIRE -> acquireSha;
            case RENEW -> renewSha;
            case COMPLETE -> completeSha;
        };
    }

    private Request scriptRequest(String sha, GatewayExecutionResourceRuntimePolicySnapshot policy,
                                  List<String> args) {
        Request request = Request.cmd(Command.EVALSHA)
                .arg(sha)
                .arg("2")
                .arg(leaseKey(policy))
                .arg(healthKey(policy));
        for (String arg : args) {
            request.arg(arg);
        }
        return request;
    }

    private Future<Response> sendWithTimeout(Request request) {
        Promise<Response> promise = Promise.promise();
        AtomicBoolean completed = new AtomicBoolean(false);
        long timerId = vertx.setTimer(config.redisCommandTimeoutMs(), ignored -> {
            if (completed.compareAndSet(false, true)) {
                promise.fail(new IllegalStateException("运行时 Redis 命令超时"));
            }
        });
        connection.send(request).onComplete(result -> {
            if (completed.compareAndSet(false, true)) {
                vertx.cancelTimer(timerId);
                if (result.succeeded()) {
                    promise.complete(result.result());
                } else {
                    promise.fail(result.cause());
                }
            }
        });
        return promise.future();
    }

    private GatewayRuntimeLeaseAcquireResult toAcquireResult(Response response) {
        String statusText = statusOf(response);
        GatewayRuntimeLeaseAcquireStatus status = GatewayRuntimeLeaseAcquireStatus.valueOf(statusText);
        if (status == GatewayRuntimeLeaseAcquireStatus.GRANTED
                || status == GatewayRuntimeLeaseAcquireStatus.HALF_OPEN_GRANTED) {
            return GatewayRuntimeLeaseAcquireResult.granted(status, response.get(1).toString());
        }
        return GatewayRuntimeLeaseAcquireResult.rejected(status);
    }

    private String statusOf(Response response) {
        if (response == null || !response.isArray() || response.size() == 0) {
            throw new IllegalStateException("运行时 Redis 脚本响应不合法");
        }
        return response.get(0).toString();
    }

    private boolean isNoScript(Throwable throwable) {
        String message = throwable == null ? "" : String.valueOf(throwable.getMessage()).toUpperCase(Locale.ROOT);
        return message.contains("NOSCRIPT");
    }

    private String leaseKey(GatewayExecutionResourceRuntimePolicySnapshot policy) {
        return "converge:gateway:runtime:{" + keyTag(policy) + "}:leases";
    }

    private String healthKey(GatewayExecutionResourceRuntimePolicySnapshot policy) {
        return "converge:gateway:runtime:{" + keyTag(policy) + "}:health";
    }

    private String keyTag(GatewayExecutionResourceRuntimePolicySnapshot policy) {
        return policy.tenantId() + ":" + policy.executionResourceId() + ":" + policy.policyVersion();
    }

    private long keyTtlMs(GatewayExecutionResourceRuntimePolicySnapshot policy) {
        long maxPolicyMs = Math.max(policy.failureCooldownMs(), policy.rateLimitCooldownMs());
        long baseMs = Math.max(maxPolicyMs, config.leaseTtlMs());
        return baseMs * 3;
    }

    private enum ScriptKind {
        ACQUIRE,
        RENEW,
        COMPLETE
    }
}
