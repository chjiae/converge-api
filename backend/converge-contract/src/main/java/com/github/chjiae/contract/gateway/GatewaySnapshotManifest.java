package com.github.chjiae.contract.gateway;

/**
 * 网关快照 Manifest。
 * Manifest 是 Redis current pointer，必须使用 HMAC 保护，不得只依赖 payload SHA-256。
 *
 * @param schemaVersion schema 版本
 * @param tenantId 租户 ID
 * @param revision 快照 revision
 * @param payloadRedisKey 不可变 payload Redis key
 * @param payloadSha256Hex payload 的 SHA-256 小写十六进制
 * @param manifestHmacBase64 Manifest HMAC-SHA-256 签名
 * @param gatewayKeyId 网关投递密钥版本
 * @param publishedAtEpochMillis 发布时间，Unix 毫秒
 */
public record GatewaySnapshotManifest(
        int schemaVersion,
        String tenantId,
        long revision,
        String payloadRedisKey,
        String payloadSha256Hex,
        String manifestHmacBase64,
        String gatewayKeyId,
        long publishedAtEpochMillis
) {

    /**
     * 返回用于签名的无 HMAC Manifest。
     *
     * @return HMAC 字段为空的 Manifest
     */
    public GatewaySnapshotManifest withoutHmac() {
        return new GatewaySnapshotManifest(schemaVersion, tenantId, revision, payloadRedisKey,
                payloadSha256Hex, "", gatewayKeyId, publishedAtEpochMillis);
    }
}
