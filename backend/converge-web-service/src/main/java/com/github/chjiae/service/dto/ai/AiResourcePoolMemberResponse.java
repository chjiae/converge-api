package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 资源池成员响应 DTO。
 */
@Data
@Builder
public class AiResourcePoolMemberResponse {

    /** 成员 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 资源池 ID */
    private Long resourcePoolId;

    /** 执行资源 ID */
    private Long executionResourceId;

    /** 管理状态 */
    private AiCatalogStatus adminStatus;

    /** 池内优先级 */
    private Integer priority;

    /** 同优先级内权重 */
    private Integer weight;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
