package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiCredentialType;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 上游凭据实体，对应 ai_credential 表。
 * 保存加密后的上游 API Key，包含密文、Nonce、HMAC 指纹和掩码预览。
 * 不保存任何明文密钥。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_credential")
public class AiCredential extends BaseEntity {

    /** 所属供应商 ID，必填且必须属于同一租户 */
    private Long providerId;

    /** 凭据编码，必填且在同一租户/供应商下唯一 */
    private String code;

    /** 凭据展示名称，必填 */
    private String displayName;

    /** 凭据描述，可选 */
    private String description;

    /** 凭据类型，本阶段仅 API_KEY */
    private AiCredentialType credentialType;

    /** 管理状态：ENABLED / DISABLED */
    private AiCatalogStatus adminStatus;

    /** 服务端生成的 UUID 引用，用于 AAD 绑定，绝不由客户端提供 */
    private String secretReference;

    /** 当前加密主密钥的版本标识 */
    private String encryptionKeyId;

    /** 加密算法，固定 AES-256-GCM */
    private String encryptionAlgorithm;

    /** AES-256-GCM 加密后的密文（含 GCM Tag） */
    private byte[] encryptedSecret;

    /** AES-GCM 随机 12 字节 Nonce */
    private byte[] nonce;

    /** HMAC 指纹（用于同租户/同供应商去重） */
    private String secretFingerprint;

    /** 掩码预览（如 sk-...abcd），不能通过它恢复秘密 */
    private String maskedPreview;

    /** 凭据版本号（轮换时递增） */
    private Integer secretVersion;

    /** 最近一次轮换时间 */
    private LocalDateTime rotatedAt;
}
