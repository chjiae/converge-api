package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Client API Key 访问组绑定响应 DTO。
 */
@Data
@Builder
public class AiClientApiKeyAccessGroupResponse {

    /** 绑定 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** Client API Key ID */
    private Long clientApiKeyId;

    /** 访问组 ID */
    private Long accessGroupId;

    /** 管理状态 */
    private AiCatalogStatus adminStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
