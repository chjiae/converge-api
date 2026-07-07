package com.github.chjiae.service.service.ai;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网关快照 Client API Key 查询行。
 * 只包含 verifier 材料，不包含 raw key 或 secret。
 */
@Data
public class GatewaySnapshotClientApiKeyRow {

    /** 租户 ID */
    private Long tenantId;

    /** Client API Key ID */
    private Long clientApiKeyId;

    /** keyId */
    private String keyId;

    /** 管理状态 */
    private String adminStatus;

    /** verifier 摘要算法 */
    private String secretHashAlgorithm;

    /** verifier salt */
    private byte[] secretVerifierSalt;

    /** verifier hash */
    private byte[] secretVerifierHash;

    /** Key 版本 */
    private Integer keyVersion;

    /** 过期时间 */
    private LocalDateTime expiresAt;
}
