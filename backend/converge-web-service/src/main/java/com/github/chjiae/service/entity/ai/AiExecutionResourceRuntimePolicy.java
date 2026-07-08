package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 执行资源运行时治理策略实体，对应 ai_execution_resource_runtime_policy 表。
 * 每个可执行资源在租户内恰好对应一条策略，用于网关运行时并发、失败熔断和 429 冷却控制。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_execution_resource_runtime_policy")
public class AiExecutionResourceRuntimePolicy extends BaseEntity {

    /** 关联的执行资源 ID，必须属于同一租户 */
    private Long executionResourceId;

    /** 最大并发请求数，0 表示不限制 */
    private Integer maxConcurrentRequests;

    /** 连续失败打开熔断阈值，必须大于 0 */
    private Integer consecutiveFailureThreshold;

    /** 连续失败计数重置窗口，单位毫秒，必须大于 0 */
    private Long failureResetAfterMs;

    /** 普通上游失败熔断冷却时间，单位毫秒，必须大于 0 */
    private Long failureCooldownMs;

    /** 上游 429 冷却时间，单位毫秒，必须大于 0 */
    private Long rateLimitCooldownMs;

    /** 策略版本，更新时单调递增 */
    private Long policyVersion;
}
