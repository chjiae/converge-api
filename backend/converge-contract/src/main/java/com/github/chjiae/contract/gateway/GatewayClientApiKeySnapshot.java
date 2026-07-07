package com.github.chjiae.contract.gateway;

/**
 * 下游 Client API Key 快照。
 * 只保存 verifier 材料，永不保存 raw key 或 secret。
 *
 * @param tenantId 租户 ID
 * @param clientApiKeyId Client API Key ID
 * @param keyId raw key 中公开可索引的 keyId
 * @param adminStatus 管理状态
 * @param secretHashAlgorithm verifier 摘要算法
 * @param secretVerifierSaltBase64 verifier salt，Base64 编码
 * @param secretVerifierHashBase64 verifier hash，Base64 编码
 * @param keyVersion key 版本，轮换时递增
 * @param expiresAtEpochMillis 过期时间 Unix 毫秒，0 表示不过期
 */
public record GatewayClientApiKeySnapshot(
        String tenantId,
        String clientApiKeyId,
        String keyId,
        String adminStatus,
        String secretHashAlgorithm,
        String secretVerifierSaltBase64,
        String secretVerifierHashBase64,
        int keyVersion,
        long expiresAtEpochMillis
) {
}
