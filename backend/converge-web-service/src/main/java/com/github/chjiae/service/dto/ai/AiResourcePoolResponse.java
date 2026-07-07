package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiSelectionPolicy;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 资源池响应 DTO。
 */
@Data
@Builder
public class AiResourcePoolResponse {

    /** 资源池 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 资源池编码 */
    private String code;

    /** 资源池展示名称 */
    private String displayName;

    /** 资源池描述 */
    private String description;

    /** 管理状态 */
    private AiCatalogStatus adminStatus;

    /** 选择策略 */
    private AiSelectionPolicy selectionPolicy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
