package com.github.chjiae.service.service.ai;

import lombok.Data;

/**
 * 网关快照执行资源运行时治理策略查询行。
 * 只包含网关运行时治理所需的安全数值，不包含任何秘密。
 */
@Data
public class GatewaySnapshotRuntimePolicyRow {

    /** 租户 ID */
    private Long tenantId;

    /** 策略 ID */
    private Long runtimePolicyId;

    /** 执行资源 ID */
    private Long executionResourceId;

    /** 策略版本 */
    private Long policyVersion;

    /** 最大并发请求数 */
    private Integer maxConcurrentRequests;

    /** 连续失败阈值 */
    private Integer consecutiveFailureThreshold;

    /** 连续失败重置窗口，单位毫秒 */
    private Long failureResetAfterMs;

    /** 普通失败冷却时间，单位毫秒 */
    private Long failureCooldownMs;

    /** 上游 429 冷却时间，单位毫秒 */
    private Long rateLimitCooldownMs;
}
