package com.github.chjiae.service.service.ai;

import com.github.chjiae.common.enums.AiCredentialType;
import com.github.chjiae.service.config.AiCredentialEncryptionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * AI 凭据 HMAC 指纹服务。
 * <p>
 * 使用独立的 HMAC 密钥（与加密主密钥分开）对明文 API Key 生成不可逆指纹，
 * 用于同租户/同供应商内去重，防止同一个 API Key 被多次录入。
 * <p>
 * 指纹计算方式：HMAC-SHA256(fingerprintKey, tenantId|providerId|credentialType|plaintextSecret)
 * 不同租户使用相同原始 API Key 时，因 tenantId 不同会产生不同指纹，不会误冲突。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCredentialFingerprintService {

    /** HMAC 算法 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 加密配置属性（使用独立的指纹密钥） */
    private final AiCredentialEncryptionProperties encryptionProperties;

    /**
     * 计算明文 API Key 的 HMAC 指纹。
     *
     * @param plaintextSecret 明文 API Key
     * @param tenantId        租户 ID（作用域隔离）
     * @param providerId      供应商 ID
     * @param credentialType  凭据类型
     * @return 十六进制编码的 HMAC 指纹字符串
     */
    public String computeFingerprint(String plaintextSecret, Long tenantId, Long providerId,
                                      AiCredentialType credentialType) {
        try {
            // 拼接指纹输入：tenantId|providerId|credentialType|plaintextSecret
            String input = tenantId + "|" + providerId + "|" + credentialType.name() + "|" + plaintextSecret;

            SecretKeySpec keySpec = new SecretKeySpec(encryptionProperties.getFingerprintKeyBytes(), HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));

            String fingerprint = HexFormat.of().formatHex(hmacBytes);
            log.debug("凭据指纹计算成功，租户 ID: {}，供应商 ID: {}", tenantId, providerId);
            return fingerprint;
        } catch (Exception e) {
            log.error("凭据指纹计算失败，租户 ID: {}，供应商 ID: {}", tenantId, providerId, e);
            throw new IllegalStateException("凭据指纹计算失败", e);
        }
    }
}
