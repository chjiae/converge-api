package com.github.chjiae.contract.gateway;

/**
 * 执行资源运行时治理策略快照。
 * 网关只使用这些安全数值做并发 lease、故障熔断、429 冷却和半开探测，不包含任何秘密。
 *
 * @param tenantId 租户 ID
 * @param runtimePolicyId 运行时策略 ID
 * @param executionResourceId 执行资源 ID
 * @param policyVersion 策略版本，更新时单调递增
 * @param maxConcurrentRequests 最大并发请求数，0 表示不限制
 * @param consecutiveFailureThreshold 连续失败阈值
 * @param failureResetAfterMs 连续失败计数重置窗口，单位毫秒
 * @param failureCooldownMs 普通上游失败熔断冷却时间，单位毫秒
 * @param rateLimitCooldownMs 上游 429 冷却时间，单位毫秒
 */
public record GatewayExecutionResourceRuntimePolicySnapshot(
        String tenantId,
        String runtimePolicyId,
        String executionResourceId,
        long policyVersion,
        int maxConcurrentRequests,
        int consecutiveFailureThreshold,
        long failureResetAfterMs,
        long failureCooldownMs,
        long rateLimitCooldownMs
) {
}
