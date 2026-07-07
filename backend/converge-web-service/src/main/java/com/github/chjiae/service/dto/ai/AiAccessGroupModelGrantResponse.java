package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCanonicalOperation;
import com.github.chjiae.common.enums.AiCatalogStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 访问组模型授权响应 DTO。
 */
@Data
@Builder
public class AiAccessGroupModelGrantResponse {

    /** 授权 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 访问组 ID */
    private Long accessGroupId;

    /** 公开模型 ID */
    private Long publicModelId;

    /** 规范化操作类型 */
    private AiCanonicalOperation canonicalOperation;

    /** 管理状态 */
    private AiCatalogStatus adminStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
