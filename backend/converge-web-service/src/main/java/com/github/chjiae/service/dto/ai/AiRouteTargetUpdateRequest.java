package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 静态路由目标更新请求。
 * 不允许修改所属策略或资源池，仅允许调整状态、优先级和权重。
 */
@Data
public class AiRouteTargetUpdateRequest {

    /** 管理状态，必填 */
    @NotNull(message = "路由目标状态不能为空")
    private AiCatalogStatus adminStatus;

    /** 目标优先级，数值越大越优先 */
    @NotNull(message = "路由目标优先级不能为空")
    @Min(value = -100000, message = "路由目标优先级不能小于 -100000")
    @Max(value = 100000, message = "路由目标优先级不能大于 100000")
    private Integer priority;

    /** 同优先级目标内正整数权重 */
    @NotNull(message = "路由目标权重不能为空")
    @Min(value = 1, message = "路由目标权重必须为正整数")
    @Max(value = 100000, message = "路由目标权重不能大于 100000")
    private Integer weight;
}
