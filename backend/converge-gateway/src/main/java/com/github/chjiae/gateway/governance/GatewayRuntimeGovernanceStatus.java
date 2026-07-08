package com.github.chjiae.gateway.governance;

/**
 * 网关运行时治理安全状态摘要。
 *
 * @param state 运行状态
 * @param redisAvailable Redis 是否可用
 * @param activeLocalLeases 本地活跃 lease 数
 * @param leaseAcquireGrantedCount lease 获取成功计数
 * @param leaseAcquireRejectedCount lease 获取拒绝计数
 * @param renewFailureCount 续租失败计数
 * @param runtimeStateUnavailableCount 运行时状态不可用计数
 */
public record GatewayRuntimeGovernanceStatus(
        String state,
        boolean redisAvailable,
        int activeLocalLeases,
        long leaseAcquireGrantedCount,
        long leaseAcquireRejectedCount,
        long renewFailureCount,
        long runtimeStateUnavailableCount
) {
}
