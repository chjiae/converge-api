package com.github.chjiae.service.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 执行资源运行时治理策略响应。
 */
@Data
@Builder
public class AiExecutionResourceRuntimePolicyResponse {

    /** 策略 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 关联执行资源 ID */
    private Long executionResourceId;

    /** 最大并发请求数，0 表示不限制 */
    private Integer maxConcurrentRequests;

    /** 连续失败打开熔断阈值 */
    private Integer consecutiveFailureThreshold;

    /** 连续失败计数重置窗口，单位毫秒 */
    private Long failureResetAfterMs;

    /** 普通上游失败熔断冷却时间，单位毫秒 */
    private Long failureCooldownMs;

    /** 上游 429 冷却时间，单位毫秒 */
    private Long rateLimitCooldownMs;

    /** 策略版本，更新时单调递增 */
    private Long policyVersion;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
