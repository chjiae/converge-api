package com.github.chjiae.gateway.snapshot;

import com.github.chjiae.contract.gateway.GatewayClientPrincipal;

/**
 * 网关本地 Client API Key 索引项。
 * 不保存 raw key，只保存 verifier 材料和已编译主体。
 *
 * @param keyId keyId
 * @param adminStatus 管理状态
 * @param keyVersion Key 版本
 * @param salt verifier salt
 * @param verifierHash verifier hash
 * @param expiresAtEpochMillis 过期时间，0 表示不过期
 * @param principal 认证成功后的主体
 */
record GatewayClientKeyIndexEntry(
        String keyId,
        String adminStatus,
        int keyVersion,
        byte[] salt,
        byte[] verifierHash,
        long expiresAtEpochMillis,
        GatewayClientPrincipal principal
) {

    /**
     * 复制字节数组，避免索引被外部修改。
     */
    GatewayClientKeyIndexEntry {
        salt = salt == null ? new byte[0] : salt.clone();
        verifierHash = verifierHash == null ? new byte[0] : verifierHash.clone();
    }
}
