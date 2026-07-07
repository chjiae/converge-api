package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 访问组响应 DTO。
 */
@Data
@Builder
public class AiAccessGroupResponse {

    /** 访问组 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 访问组编码 */
    private String code;

    /** 访问组展示名称 */
    private String displayName;

    /** 访问组描述 */
    private String description;

    /** 管理状态 */
    private AiCatalogStatus adminStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
