package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiClientApiKeyStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Client API Key 一次性明文响应 DTO。
 * 仅 create/rotate 返回 rawKey，调用方必须立即保存。
 */
@Data
@Builder
public class AiClientApiKeySecretResponse {

    /** Key ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** Key 编码 */
    private String code;

    /** Key 展示名称 */
    private String displayName;

    /** Key 描述 */
    private String description;

    /** raw key 中公开可索引的 keyId */
    private String keyId;

    /** 管理状态 */
    private AiClientApiKeyStatus adminStatus;

    /** 掩码预览 */
    private String maskedPreview;

    /** Key 版本 */
    private Integer keyVersion;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 最近轮换时间 */
    private LocalDateTime rotatedAt;

    /** 撤销时间 */
    private LocalDateTime revokedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 一次性返回的 raw key，服务端不会持久化 */
    private String rawKey;
}
