package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCanonicalOperation;
import com.github.chjiae.common.enums.AiCatalogStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 资源模型绑定响应 DTO。
 */
@Data
@Builder
public class AiResourceModelBindingResponse {

    /** 绑定 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 执行资源 ID */
    private Long executionResourceId;

    /** 公开模型 ID */
    private Long publicModelId;

    /** 规范化操作类型 */
    private AiCanonicalOperation canonicalOperation;

    /** 精确上游模型名 */
    private String upstreamModelName;

    /** 管理状态 */
    private AiCatalogStatus adminStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
