package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 资源池成员创建请求。
 * 只允许写入执行资源、状态、优先级和权重，不允许写 tenantId。
 */
@Data
public class AiResourcePoolMemberCreateRequest {

    /** 执行资源 ID，必填且必须属于当前租户 */
    @NotNull(message = "执行资源 ID 不能为空")
    private Long executionResourceId;

    /** 管理状态，必填 */
    @NotNull(message = "成员状态不能为空")
    private AiCatalogStatus adminStatus;

    /** 池内优先级，数值越大越优先 */
    @NotNull(message = "成员优先级不能为空")
    @Min(value = -100000, message = "成员优先级不能小于 -100000")
    @Max(value = 100000, message = "成员优先级不能大于 100000")
    private Integer priority;

    /** 同优先级内正整数权重 */
    @NotNull(message = "成员权重不能为空")
    @Min(value = 1, message = "成员权重必须为正整数")
    @Max(value = 100000, message = "成员权重不能大于 100000")
    private Integer weight;
}
