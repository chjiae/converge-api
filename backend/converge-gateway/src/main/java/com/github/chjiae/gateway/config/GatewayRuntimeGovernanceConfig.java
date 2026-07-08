package com.github.chjiae.gateway.config;

/**
 * 网关运行时治理配置。
 *
 * @param redisCommandTimeoutMs Redis 命令超时时间，单位毫秒
 * @param leaseTtlMs 运行时 lease TTL，单位毫秒
 * @param leaseRenewIntervalMs SSE lease 续租间隔，单位毫秒
 * @param maxCandidateAttempts 单请求最大候选尝试数
 */
public record GatewayRuntimeGovernanceConfig(
        long redisCommandTimeoutMs,
        long leaseTtlMs,
        long leaseRenewIntervalMs,
        int maxCandidateAttempts
) {

    /** 默认 Redis 命令超时时间 */
    public static final long DEFAULT_REDIS_COMMAND_TIMEOUT_MS = 1500L;

    /** 默认 lease TTL */
    public static final long DEFAULT_LEASE_TTL_MS = 120_000L;

    /** 默认 lease 续租间隔 */
    public static final long DEFAULT_LEASE_RENEW_INTERVAL_MS = 30_000L;

    /** 默认最大候选尝试数 */
    public static final int DEFAULT_MAX_CANDIDATE_ATTEMPTS = 64;

    /**
     * 校验运行时治理配置。
     */
    public GatewayRuntimeGovernanceConfig {
        if (redisCommandTimeoutMs <= 0) {
            throw new IllegalArgumentException("运行时治理 Redis 命令超时时间必须大于 0");
        }
        if (leaseTtlMs <= 0) {
            throw new IllegalArgumentException("运行时治理 lease TTL 必须大于 0");
        }
        if (leaseRenewIntervalMs <= 0) {
            throw new IllegalArgumentException("运行时治理 lease 续租间隔必须大于 0");
        }
        if (leaseRenewIntervalMs >= leaseTtlMs / 2) {
            throw new IllegalArgumentException("运行时治理 lease 续租间隔必须小于 lease TTL 的一半");
        }
        if (redisCommandTimeoutMs >= leaseRenewIntervalMs) {
            throw new IllegalArgumentException("运行时治理 Redis 命令超时时间必须小于 lease 续租间隔");
        }
        if (maxCandidateAttempts <= 0 || maxCandidateAttempts > 1024) {
            throw new IllegalArgumentException("运行时治理最大候选尝试数必须在 1 到 1024 之间");
        }
    }

    /**
     * 默认运行时治理配置。
     *
     * @return 默认配置
     */
    public static GatewayRuntimeGovernanceConfig defaults() {
        return new GatewayRuntimeGovernanceConfig(DEFAULT_REDIS_COMMAND_TIMEOUT_MS,
                DEFAULT_LEASE_TTL_MS,
                DEFAULT_LEASE_RENEW_INTERVAL_MS,
                DEFAULT_MAX_CANDIDATE_ATTEMPTS);
    }
}
