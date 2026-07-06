package com.github.chjiae.service.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.security.MessageDigest;
import java.util.Base64;

/**
 * AI 网关快照发布配置。
 * 控制面使用独立网关投递密钥重新封装凭据，严禁复用数据库凭据加密密钥。
 */
@Slf4j
@Getter
@Configuration
public class GatewaySnapshotProperties {

    /** 网关投递密钥版本标识 */
    @Value("${ai.gateway.snapshot.key-id:}")
    private String keyId;

    /** 网关投递 AES-256-GCM 加密密钥，Base64 编码 */
    @Value("${ai.gateway.snapshot.encryption-key-base64:}")
    private String encryptionKeyBase64;

    /** 网关 Manifest HMAC-SHA-256 签名密钥，Base64 编码 */
    @Value("${ai.gateway.snapshot.signing-key-base64:}")
    private String signingKeyBase64;

    /** outbox 单批认领数量 */
    @Value("${ai.gateway.snapshot.outbox-batch-size:50}")
    private int outboxBatchSize;

    /** outbox 锁超时时间，毫秒 */
    @Value("${ai.gateway.snapshot.outbox-lock-timeout-ms:30000}")
    private long outboxLockTimeoutMs;

    /** outbox 周期扫描间隔，毫秒 */
    @Value("${ai.gateway.snapshot.outbox-projector-interval-ms:5000}")
    private long outboxProjectorIntervalMs;

    /** 全租户重投影间隔，毫秒 */
    @Value("${ai.gateway.snapshot.reproject-interval-ms:60000}")
    private long reprojectIntervalMs;

    /** Redis 历史保留数量，包含 current 和旧版本 */
    @Value("${ai.gateway.snapshot.history-retain-count:3}")
    private int historyRetainCount;

    /** 解码后的网关投递加密密钥 */
    private byte[] encryptionKeyBytes;

    /** 解码后的网关签名密钥 */
    private byte[] signingKeyBytes;

    /** 阶段 03 数据库凭据加密配置，用于校验密钥不复用 */
    private final AiCredentialEncryptionProperties credentialEncryptionProperties;

    /**
     * 创建配置对象。
     *
     * @param credentialEncryptionProperties 数据库凭据加密配置
     */
    public GatewaySnapshotProperties(AiCredentialEncryptionProperties credentialEncryptionProperties) {
        this.credentialEncryptionProperties = credentialEncryptionProperties;
    }

    /**
     * 启动时严格校验网关投递密钥。
     */
    @PostConstruct
    public void validateAndInit() {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalStateException("AI 网关快照密钥 ID 未配置");
        }
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            throw new IllegalStateException("AI 网关快照加密密钥未配置");
        }
        if (signingKeyBase64 == null || signingKeyBase64.isBlank()) {
            throw new IllegalStateException("AI 网关快照签名密钥未配置");
        }
        encryptionKeyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        signingKeyBytes = Base64.getDecoder().decode(signingKeyBase64);
        if (encryptionKeyBytes.length != 32) {
            throw new IllegalStateException("AI 网关快照加密密钥长度必须为 32 字节");
        }
        if (signingKeyBytes.length < 32) {
            throw new IllegalStateException("AI 网关快照签名密钥长度至少为 32 字节");
        }
        if (MessageDigest.isEqual(encryptionKeyBytes, signingKeyBytes)) {
            throw new IllegalStateException("AI 网关快照加密密钥和签名密钥不能相同");
        }
        if (MessageDigest.isEqual(encryptionKeyBytes, credentialEncryptionProperties.getActiveKeyBytes())
                || MessageDigest.isEqual(encryptionKeyBytes, credentialEncryptionProperties.getFingerprintKeyBytes())
                || MessageDigest.isEqual(signingKeyBytes, credentialEncryptionProperties.getActiveKeyBytes())
                || MessageDigest.isEqual(signingKeyBytes, credentialEncryptionProperties.getFingerprintKeyBytes())) {
            throw new IllegalStateException("AI 网关快照投递密钥不能复用阶段 03 数据库凭据密钥");
        }
        if (outboxBatchSize <= 0 || historyRetainCount < 3) {
            throw new IllegalStateException("AI 网关快照 outbox 批量和历史保留配置不合法");
        }
        log.info("AI 网关快照配置初始化成功，密钥版本: {}", keyId);
    }
}
