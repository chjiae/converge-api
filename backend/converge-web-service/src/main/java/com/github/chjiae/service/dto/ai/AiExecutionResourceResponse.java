package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiResourceStatus;
import com.github.chjiae.common.enums.AiResourceType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 可执行资源响应 DTO。
 */
@Data
@Builder
public class AiExecutionResourceResponse {

    /** 资源 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 所属供应商 ID */
    private Long providerId;

    /** 关联的上游连接 ID */
    private Long upstreamConnectionId;

    /** 关联的凭据 ID */
    private Long credentialId;

    /** 资源类型 */
    private AiResourceType resourceType;

    /** 资源编码 */
    private String code;

    /** 资源展示名称 */
    private String displayName;

    /** 资源描述 */
    private String description;

    /** 管理状态 */
    private AiResourceStatus adminStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
