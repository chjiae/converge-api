package com.github.chjiae.service.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

/**
 * AI 凭据加密配置属性。
 * 从环境变量读取加密主密钥和 HMAC 指纹密钥，启动时严格校验配置完整性。
 * 生产环境不提供任何默认值，缺少必要密钥时应用启动阶段明确失败。
 */
@Slf4j
@Data
@Configuration
public class AiCredentialEncryptionProperties {

    /** 当前活跃加密密钥版本标识 */
    @Value("${ai.credential.encryption.active-key-id:}")
    private String activeKeyId;

    /** 当前活跃加密密钥（Base64 编码的 256 位密钥） */
    @Value("${ai.credential.encryption.active-key-base64:}")
    private String activeKeyBase64;

    /** HMAC 指纹密钥（Base64 编码的 256 位密钥），与加密主密钥必须分开 */
    @Value("${ai.credential.encryption.fingerprint-key-base64:}")
    private String fingerprintKeyBase64;

    /** 解密后的加密主密钥字节数组 */
    private byte[] activeKeyBytes;

    /** 解密后的 HMAC 指纹密钥字节数组 */
    private byte[] fingerprintKeyBytes;

    /**
     * 应用启动时校验密钥配置完整性。
     * 生产环境（非测试 Profile）缺少任何必要密钥时直接抛异常，不允许悄悄降级。
     */
    @PostConstruct
    public void validateAndInit() {
        boolean hasKeyId = activeKeyId != null && !activeKeyId.isBlank();
        boolean hasKeyBase64 = activeKeyBase64 != null && !activeKeyBase64.isBlank();
        boolean hasFingerprintBase64 = fingerprintKeyBase64 != null && !fingerprintKeyBase64.isBlank();

        if (!hasKeyId || !hasKeyBase64 || !hasFingerprintBase64) {
            String missing = String.join(", ",
                    !hasKeyId ? "ai.credential.encryption.active-key-id" : "",
                    !hasKeyBase64 ? "ai.credential.encryption.active-key-base64" : "",
                    !hasFingerprintBase64 ? "ai.credential.encryption.fingerprint-key-base64" : ""
            ).replaceAll("^,\\s*", "");

            log.error("AI 凭据加密密钥配置不完整，缺少: {}。请设置对应的环境变量 AI_CREDENTIAL_ACTIVE_KEY_ID、"
                    + "AI_CREDENTIAL_ACTIVE_KEY_BASE64、AI_CREDENTIAL_FINGERPRINT_KEY_BASE64", missing);
            throw new IllegalStateException("AI 凭据加密密钥配置不完整，缺少: " + missing);
        }

        // 解码 Base64 密钥
        activeKeyBytes = Base64.getDecoder().decode(activeKeyBase64);
        fingerprintKeyBytes = Base64.getDecoder().decode(fingerprintKeyBase64);

        // 校验密钥长度：AES-256 要求 32 字节
        if (activeKeyBytes.length != 32) {
            throw new IllegalStateException(
                    "AI 凭据加密主密钥长度不正确，期望 32 字节（256 位），实际: " + activeKeyBytes.length + " 字节");
        }
        // HMAC-SHA256 推荐 32 字节
        if (fingerprintKeyBytes.length != 32) {
            throw new IllegalStateException(
                    "AI 凭据指纹密钥长度不正确，期望 32 字节（256 位），实际: " + fingerprintKeyBytes.length + " 字节");
        }

        // 加密主密钥和 HMAC 指纹密钥必须不同
        if (java.security.MessageDigest.isEqual(activeKeyBytes, fingerprintKeyBytes)) {
            throw new IllegalStateException("AI 凭据加密主密钥和 HMAC 指纹密钥不能相同");
        }

        log.info("AI 凭据加密配置初始化成功，密钥版本: {}", activeKeyId);
    }
}
