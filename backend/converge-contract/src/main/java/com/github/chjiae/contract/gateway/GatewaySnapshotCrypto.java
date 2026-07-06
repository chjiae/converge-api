package com.github.chjiae.contract.gateway;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 网关快照摘要、签名与秘密封装工具。
 * 只使用 JDK 密码学 API，不依赖 Spring、Vert.x、Redis 或数据库。
 */
public final class GatewaySnapshotCrypto {

    /** 安全随机数生成器。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private GatewaySnapshotCrypto() {
    }

    /**
     * 计算 SHA-256 小写十六进制摘要。
     *
     * @param payload payload 字节
     * @return 小写十六进制摘要
     */
    public static String sha256Hex(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (Exception e) {
            throw new IllegalStateException("计算快照 SHA-256 失败", e);
        }
    }

    /**
     * 签名 Manifest。
     *
     * @param manifest HMAC 字段为空或已忽略的 Manifest
     * @param signingKey HMAC-SHA-256 签名密钥
     * @return Base64 HMAC
     */
    public static String signManifest(GatewaySnapshotManifest manifest, byte[] signingKey) {
        try {
            Mac mac = Mac.getInstance(GatewaySnapshotSchema.MANIFEST_HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, GatewaySnapshotSchema.MANIFEST_HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(manifestSigningInput(manifest)));
        } catch (Exception e) {
            throw new IllegalStateException("签名网关快照 Manifest 失败", e);
        }
    }

    /**
     * 验证 Manifest HMAC。
     *
     * @param manifest 待验证 Manifest
     * @param signingKey HMAC-SHA-256 签名密钥
     * @return true 表示签名匹配
     */
    public static boolean verifyManifest(GatewaySnapshotManifest manifest, byte[] signingKey) {
        String expected = signManifest(manifest.withoutHmac(), signingKey);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                manifest.manifestHmacBase64().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构造 Manifest 固定签名输入。
     *
     * @param manifest Manifest
     * @return UTF-8 字节
     */
    public static byte[] manifestSigningInput(GatewaySnapshotManifest manifest) {
        String input = manifest.schemaVersion() + "\n"
                + manifest.tenantId() + "\n"
                + manifest.revision() + "\n"
                + manifest.payloadRedisKey() + "\n"
                + manifest.payloadSha256Hex() + "\n"
                + manifest.gatewayKeyId();
        return input.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 构造秘密 envelope 的 AES-GCM AAD。
     *
     * @param schemaVersion schema 版本
     * @param tenantId 租户 ID
     * @param executionResourceId 执行资源 ID
     * @param credentialId 凭据 ID
     * @param snapshotRevision 快照 revision
     * @param gatewayKeyId 网关投递密钥 ID
     * @return AAD 字节
     */
    public static byte[] secretAad(int schemaVersion, String tenantId, String executionResourceId,
                                   String credentialId, long snapshotRevision, String gatewayKeyId) {
        String aad = schemaVersion + "\n"
                + tenantId + "\n"
                + executionResourceId + "\n"
                + credentialId + "\n"
                + snapshotRevision + "\n"
                + gatewayKeyId;
        return aad.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 使用网关投递密钥封装秘密。
     *
     * @param plaintextSecret 明文秘密
     * @param encryptionKey AES-256 密钥
     * @param aad AAD
     * @param keyId 网关密钥 ID
     * @return 秘密 envelope
     */
    public static GatewaySecretEnvelope encryptSecret(String plaintextSecret, byte[] encryptionKey,
                                                      byte[] aad, String keyId) {
        try {
            byte[] nonce = new byte[GatewaySnapshotSchema.GCM_NONCE_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(GatewaySnapshotSchema.AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(GatewaySnapshotSchema.GCM_TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(plaintextSecret.getBytes(StandardCharsets.UTF_8));
            return new GatewaySecretEnvelope(keyId, GatewaySnapshotSchema.SECRET_ALGORITHM,
                    Base64.getEncoder().encodeToString(nonce),
                    Base64.getEncoder().encodeToString(ciphertext));
        } catch (Exception e) {
            throw new IllegalStateException("封装网关快照秘密失败", e);
        }
    }

    /**
     * 解封装秘密，主要用于网关验证和运行时内存持有。
     *
     * @param envelope 秘密 envelope
     * @param encryptionKey AES-256 密钥
     * @param aad AAD
     * @param expectedKeyId 期望密钥 ID
     * @return 明文秘密
     */
    public static String decryptSecret(GatewaySecretEnvelope envelope, byte[] encryptionKey,
                                       byte[] aad, String expectedKeyId) {
        if (!GatewaySnapshotSchema.SECRET_ALGORITHM.equals(envelope.algorithm())) {
            throw new IllegalArgumentException("不支持的网关快照秘密算法");
        }
        if (!expectedKeyId.equals(envelope.keyId())) {
            throw new IllegalArgumentException("网关快照秘密 keyId 不匹配");
        }
        try {
            byte[] nonce = Base64.getDecoder().decode(envelope.nonceBase64());
            byte[] ciphertext = Base64.getDecoder().decode(envelope.ciphertextBase64());
            Cipher cipher = Cipher.getInstance(GatewaySnapshotSchema.AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(GatewaySnapshotSchema.GCM_TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("网关快照秘密解封装失败", e);
        }
    }
}
