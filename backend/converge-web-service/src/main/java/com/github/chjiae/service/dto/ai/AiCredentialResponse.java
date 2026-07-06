package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiCredentialType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 凭据响应 DTO。
 * 不包含任何明文 API Key 或密文数据，仅返回管理元数据和安全掩码预览。
 */
@Data
@Builder
public class AiCredentialResponse {

    /** 凭据 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 所属供应商 ID */
    private Long providerId;

    /** 凭据编码 */
    private String code;

    /** 凭据展示名称 */
    private String displayName;

    /** 凭据描述 */
    private String description;

    /** 凭据类型 */
    private AiCredentialType credentialType;

    /** 管理状态 */
    private AiCatalogStatus adminStatus;

    /** 掩码预览（如 sk-...abcd） */
    private String maskedPreview;

    /** 凭据版本号 */
    private Integer secretVersion;

    /** 最近一次轮换时间 */
    private LocalDateTime rotatedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
