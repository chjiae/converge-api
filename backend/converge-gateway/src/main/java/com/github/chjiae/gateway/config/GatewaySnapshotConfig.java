package com.github.chjiae.gateway.config;

import java.security.MessageDigest;
import java.util.Base64;

/**
 * 网关快照同步配置。
 * 只读取网关投递密钥，严禁读取控制面数据库凭据加密密钥。
 *
 * @param redisUri Redis 连接 URI
 * @param keyId 网关投递密钥 ID
 * @param encryptionKeyBase64 AES-256-GCM 投递密钥
 * @param signingKeyBase64 Manifest HMAC 签名密钥
 * @param reconcileIntervalMs 周期对账间隔
 * @param maxStalenessMs 最大快照陈旧时间
 * @param historyRetainCount 历史保留数量
 */
public record GatewaySnapshotConfig(
        String redisUri,
        String keyId,
        String encryptionKeyBase64,
        String signingKeyBase64,
        long reconcileIntervalMs,
        long maxStalenessMs,
        int historyRetainCount
) {

    /**
     * 校验配置完整性。
     */
    public GatewaySnapshotConfig {
        if (redisUri == null || redisUri.isBlank()) {
            throw new IllegalArgumentException("网关 Redis URI 不能为空");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("网关快照密钥 ID 不能为空");
        }
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            throw new IllegalArgumentException("网关快照加密密钥不能为空");
        }
        if (signingKeyBase64 == null || signingKeyBase64.isBlank()) {
            throw new IllegalArgumentException("网关快照签名密钥不能为空");
        }
        if (reconcileIntervalMs <= 0 || maxStalenessMs <= 0) {
            throw new IllegalArgumentException("网关快照对账和陈旧时间配置必须大于 0");
        }
        if (historyRetainCount < 3) {
            throw new IllegalArgumentException("网关快照历史保留数量不能小于 3");
        }
        byte[] encryptionKey = decodeKey(encryptionKeyBase64, "网关快照加密密钥");
        byte[] signingKey = decodeKey(signingKeyBase64, "网关快照签名密钥");
        if (encryptionKey.length != 32) {
            throw new IllegalArgumentException("网关快照加密密钥长度必须为 32 字节");
        }
        if (signingKey.length < 32) {
            throw new IllegalArgumentException("网关快照签名密钥长度至少为 32 字节");
        }
        if (MessageDigest.isEqual(encryptionKey, signingKey)) {
            throw new IllegalArgumentException("网关快照加密密钥和签名密钥不能相同");
        }
    }

    /**
     * 获取解码后的加密密钥。
     *
     * @return 32 字节 AES 密钥
     */
    public byte[] encryptionKeyBytes() {
        return Base64.getDecoder().decode(encryptionKeyBase64);
    }

    /**
     * 获取解码后的签名密钥。
     *
     * @return HMAC 签名密钥
     */
    public byte[] signingKeyBytes() {
        return Base64.getDecoder().decode(signingKeyBase64);
    }

    private static byte[] decodeKey(String base64, String name) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + "必须是合法 Base64", e);
        }
    }
}
