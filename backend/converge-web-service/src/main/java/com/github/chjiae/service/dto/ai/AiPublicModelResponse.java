package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 公开模型响应 DTO。
 * 仅返回公开模型目录元数据，不包含上游模型映射。
 */
@Data
@Builder
public class AiPublicModelResponse {

    /** 公开模型 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 下游公开模型别名 */
    private String code;

    /** 公开模型展示名称 */
    private String displayName;

    /** 模型族或产品线 */
    private String modelFamily;

    /** 目录状态 */
    private AiCatalogStatus status;

    /** 公开模型描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
