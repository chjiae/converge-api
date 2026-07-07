package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 静态路由目标响应 DTO。
 */
@Data
@Builder
public class AiRouteTargetResponse {

    /** 目标 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 路由策略 ID */
    private Long routePolicyId;

    /** 资源池 ID */
    private Long resourcePoolId;

    /** 管理状态 */
    private AiCatalogStatus adminStatus;

    /** 目标优先级 */
    private Integer priority;

    /** 同优先级目标内权重 */
    private Integer weight;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
