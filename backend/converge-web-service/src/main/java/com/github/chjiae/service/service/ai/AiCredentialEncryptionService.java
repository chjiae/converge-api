package com.github.chjiae.service.service.ai;

import com.github.chjiae.common.enums.AiCredentialType;
import com.github.chjiae.service.config.AiCredentialEncryptionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * AI 凭据 AES-256-GCM 加密/解密服务。
 * <p>
 * 加密参数：
 * <ul>
 *   <li>算法：AES/GCM/NoPadding（JDK JCA/JCE）</li>
 *   <li>Nonce：每次加密生成随机 12 字节</li>
 *   <li>GCM Tag：128 位</li>
 *   <li>AAD 绑定：tenantId | providerId | secretReference | credentialType | schemaVersion</li>
 * </ul>
 * 密文包含 GCM Tag（附加在密文末尾），不提供任何明文降级路径。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCredentialEncryptionService {

    /** GCM 标准 Nonce 长度：12 字节 */
    private static final int GCM_NONCE_LENGTH = 12;

    /** GCM Tag 长度：128 位 */
    private static final int GCM_TAG_LENGTH_BITS = 128;

    /** AAD 绑定格式版本号，后续如需调整 AAD 结构可据此版本迁移 */
    private static final String SCHEMA_VERSION = "1";

    /** AES-GCM 算法名称 */
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    /** 安全随机数生成器 */
    private final SecureRandom secureRandom = new SecureRandom();

    /** 加密配置属性 */
    private final AiCredentialEncryptionProperties encryptionProperties;

    /**
     * 加密明文 API Key。
     *
     * @param plaintextSecret 明文 API Key
     * @param tenantId        租户 ID（AAD 绑定）
     * @param providerId      供应商 ID（AAD 绑定）
     * @param secretReference 服务端生成的 UUID 引用（AAD 绑定）
     * @param credentialType  凭据类型（AAD 绑定）
     * @return 加密结果，包含密文和 Nonce
     */
    public EncryptionResult encrypt(String plaintextSecret, Long tenantId, Long providerId,
                                     String secretReference, AiCredentialType credentialType) {
        try {
            // 生成随机 12 字节 Nonce
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            secureRandom.nextBytes(nonce);

            // 构建 AAD：tenantId | providerId | secretReference | credentialType | schemaVersion
            byte[] aad = buildAad(tenantId, providerId, secretReference, credentialType);

            // 初始化 Cipher
            SecretKeySpec keySpec = new SecretKeySpec(encryptionProperties.getActiveKeyBytes(), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            cipher.updateAAD(aad);

            // 加密（输出包含 GCM Tag）
            byte[] encrypted = cipher.doFinal(plaintextSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            log.debug("凭据加密成功，租户 ID: {}，供应商 ID: {}，密文长度: {} 字节", tenantId, providerId, encrypted.length);
            return new EncryptionResult(encrypted, nonce);
        } catch (Exception e) {
            log.error("凭据加密失败，租户 ID: {}，供应商 ID: {}", tenantId, providerId, e);
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    /**
     * 解密密文还原明文 API Key。仅在控制面内部必要业务路径使用。
     *
     * @param encryptedSecret 密文（含 GCM Tag）
     * @param nonce           加密时使用的 Nonce
     * @param tenantId        租户 ID（AAD 绑定验证）
     * @param providerId      供应商 ID（AAD 绑定验证）
     * @param secretReference 服务端 UUID 引用（AAD 绑定验证）
     * @param credentialType  凭据类型（AAD 绑定验证）
     * @return 明文 API Key
     * @throws IllegalStateException 密文被篡改或 AAD 绑定信息不匹配时抛出
     */
    public String decrypt(byte[] encryptedSecret, byte[] nonce, Long tenantId, Long providerId,
                           String secretReference, AiCredentialType credentialType) {
        try {
            // 重建 AAD
            byte[] aad = buildAad(tenantId, providerId, secretReference, credentialType);

            // 初始化 Cipher 用于解密
            SecretKeySpec keySpec = new SecretKeySpec(encryptionProperties.getActiveKeyBytes(), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            cipher.updateAAD(aad);

            // 解密（自动验证 GCM Tag）
            byte[] decrypted = cipher.doFinal(encryptedSecret);

            log.debug("凭据解密成功，租户 ID: {}，供应商 ID: {}", tenantId, providerId);
            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("凭据解密失败（密文可能被篡改或 AAD 绑定信息不匹配），租户 ID: {}，供应商 ID: {}",
                    tenantId, providerId, e);
            throw new IllegalStateException("凭据解密失败，密文可能被篡改", e);
        }
    }

    /**
     * 获取当前活跃的加密密钥版本标识。
     *
     * @return 密钥版本标识
     */
    public String getActiveKeyId() {
        return encryptionProperties.getActiveKeyId();
    }

    /**
     * 构建 AAD（附加认证数据），绑定稳定上下文信息。
     * 格式：tenantId|providerId|secretReference|credentialType|schemaVersion
     */
    private byte[] buildAad(Long tenantId, Long providerId, String secretReference,
                             AiCredentialType credentialType) {
        String aadString = tenantId + "|" + providerId + "|" + secretReference
                + "|" + credentialType.name() + "|" + SCHEMA_VERSION;
        return aadString.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 加密结果，包含密文和 Nonce。
     *
     * @param encryptedSecret 密文（含 GCM Tag）
     * @param nonce           随机 12 字节 Nonce
     */
    public record EncryptionResult(byte[] encryptedSecret, byte[] nonce) {
    }
}
