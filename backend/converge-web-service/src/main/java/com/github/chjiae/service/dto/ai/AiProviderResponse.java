package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiProviderKind;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 供应商响应 DTO。
 * 返回租户内供应商的非敏感元数据。
 */
@Data
@Builder
public class AiProviderResponse {

    /** 供应商 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 租户内供应商编码 */
    private String code;

    /** 供应商展示名称 */
    private String displayName;

    /** 供应商类型 */
    private AiProviderKind providerKind;

    /** 目录状态 */
    private AiCatalogStatus status;

    /** 供应商描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
