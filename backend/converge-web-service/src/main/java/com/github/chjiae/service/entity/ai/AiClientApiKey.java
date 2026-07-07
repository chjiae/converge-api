package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiClientApiKeyStatus;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 下游 Client API Key 实体，对应 ai_client_api_key 表。
 * 表内只保存随机 salt 与 SHA-256 verifier，不保存 raw key 或 secret。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_client_api_key")
public class AiClientApiKey extends BaseEntity {

    /** Key 编码，必填且在租户内唯一 */
    private String code;

    /** Key 展示名称，必填 */
    private String displayName;

    /** Key 描述，可选 */
    private String description;

    /** raw key 中公开可索引的 keyId，全局唯一 */
    private String keyId;

    /** 管理状态：ENABLED / DISABLED / REVOKED */
    private AiClientApiKeyStatus adminStatus;

    /** verifier 摘要算法，固定 SHA-256 */
    private String secretHashAlgorithm;

    /** verifier 随机 salt，固定 16 字节 */
    private byte[] secretVerifierSalt;

    /** verifier hash，固定 32 字节 */
    private byte[] secretVerifierHash;

    /** 掩码预览，不能恢复 raw key */
    private String maskedPreview;

    /** Key 版本号，轮换时递增 */
    private Integer keyVersion;

    /** 过期时间，可为空 */
    private LocalDateTime expiresAt;

    /** 最近一次轮换时间 */
    private LocalDateTime rotatedAt;

    /** 撤销时间，REVOKED 终态时写入 */
    private LocalDateTime revokedAt;
}
