package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiProtocolType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 上游连接响应 DTO。
 * 返回连接的非敏感地址和协议元数据。
 */
@Data
@Builder
public class AiUpstreamConnectionResponse {

    /** 连接 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 所属供应商 ID */
    private Long providerId;

    /** 供应商内连接编码 */
    private String code;

    /** 连接展示名称 */
    private String displayName;

    /** 上游协议类型 */
    private AiProtocolType protocolType;

    /** 已规范化的 Base URL */
    private String baseUrl;

    /** 目录状态 */
    private AiCatalogStatus status;

    /** 连接描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
