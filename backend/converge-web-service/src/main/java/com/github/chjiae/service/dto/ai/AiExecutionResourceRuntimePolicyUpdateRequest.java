package com.github.chjiae.service.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 执行资源运行时治理策略更新请求。
 * 请求不得包含 tenantId、内部快照字段或任何密钥材料。
 */
@Data
public class AiExecutionResourceRuntimePolicyUpdateRequest {

    /** 最大并发请求数，0 表示不限制 */
    @NotNull(message = "最大并发请求数不能为空")
    @Min(value = 0, message = "最大并发请求数不能小于 0")
    @Max(value = 100000, message = "最大并发请求数不能超过 100000")
    private Integer maxConcurrentRequests;

    /** 连续失败打开熔断阈值 */
    @NotNull(message = "连续失败阈值不能为空")
    @Min(value = 1, message = "连续失败阈值必须大于 0")
    @Max(value = 100000, message = "连续失败阈值不能超过 100000")
    private Integer consecutiveFailureThreshold;

    /** 连续失败计数重置窗口，单位毫秒 */
    @NotNull(message = "失败重置窗口不能为空")
    @Min(value = 1, message = "失败重置窗口必须大于 0")
    private Long failureResetAfterMs;

    /** 普通上游失败熔断冷却时间，单位毫秒 */
    @NotNull(message = "失败冷却时间不能为空")
    @Min(value = 1, message = "失败冷却时间必须大于 0")
    private Long failureCooldownMs;

    /** 上游 429 冷却时间，单位毫秒 */
    @NotNull(message = "429 冷却时间不能为空")
    @Min(value = 1, message = "429 冷却时间必须大于 0")
    private Long rateLimitCooldownMs;
}
