package com.github.chjiae.service;

import com.github.chjiae.common.enums.AiCredentialType;
import com.github.chjiae.service.config.AiCredentialEncryptionProperties;
import com.github.chjiae.service.service.ai.AiCredentialEncryptionService;
import com.github.chjiae.service.service.ai.AiCredentialFingerprintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 凭据密码学单元测试。
 * 覆盖 AES-256-GCM 加密/解密正确性、AAD 绑定验证、篡改检测、
 * HMAC 指纹一致性和跨租户隔离。
 */
class AiCredentialCryptoUnitTest {

    /** 测试用加密主密钥（32 字节全 0x01） */
    private static final byte[] TEST_ENCRYPTION_KEY = new byte[32];

    /** 测试用 HMAC 指纹密钥（32 字节全 0x02） */
    private static final byte[] TEST_FINGERPRINT_KEY = new byte[32];

    private AiCredentialEncryptionService encryptionService;
    private AiCredentialFingerprintService fingerprintService;

    static {
        java.util.Arrays.fill(TEST_ENCRYPTION_KEY, (byte) 0x01);
        java.util.Arrays.fill(TEST_FINGERPRINT_KEY, (byte) 0x02);
    }

    @BeforeEach
    void setUp() {
        AiCredentialEncryptionProperties props = new AiCredentialEncryptionProperties();
        props.setActiveKeyId("test-key-v1");
        props.setActiveKeyBase64(Base64.getEncoder().encodeToString(TEST_ENCRYPTION_KEY));
        props.setFingerprintKeyBase64(Base64.getEncoder().encodeToString(TEST_FINGERPRINT_KEY));
        props.validateAndInit();

        encryptionService = new AiCredentialEncryptionService(props);
        fingerprintService = new AiCredentialFingerprintService(props);
    }

    @Test
    void 加密解密往返验证() {
        String apiKey = "sk-test-abcdef1234567890";
        Long tenantId = 1L;
        Long providerId = 10L;
        String secretRef = "uuid-ref-001";

        AiCredentialEncryptionService.EncryptionResult result =
                encryptionService.encrypt(apiKey, tenantId, providerId, secretRef, AiCredentialType.API_KEY);

        // 密文和 Nonce 不为空
        assertThat(result.encryptedSecret()).isNotEmpty();
        assertThat(result.nonce()).hasSize(12);

        // 解密还原
        String decrypted = encryptionService.decrypt(result.encryptedSecret(), result.nonce(),
                tenantId, providerId, secretRef, AiCredentialType.API_KEY);
        assertThat(decrypted).isEqualTo(apiKey);
    }

    @Test
    void 每次加密生成不同Nonce和密文() {
        String apiKey = "sk-test-same-key";
        Long tenantId = 1L;
        Long providerId = 10L;
        String secretRef = "uuid-ref-002";

        AiCredentialEncryptionService.EncryptionResult result1 =
                encryptionService.encrypt(apiKey, tenantId, providerId, secretRef, AiCredentialType.API_KEY);
        AiCredentialEncryptionService.EncryptionResult result2 =
                encryptionService.encrypt(apiKey, tenantId, providerId, secretRef, AiCredentialType.API_KEY);

        // Nonce 不同（随机生成）
        assertThat(result1.nonce()).isNotEqualTo(result2.nonce());
        // 密文不同（因 Nonce 不同）
        assertThat(result1.encryptedSecret()).isNotEqualTo(result2.encryptedSecret());

        // 但都能正确解密
        assertThat(encryptionService.decrypt(result1.encryptedSecret(), result1.nonce(),
                tenantId, providerId, secretRef, AiCredentialType.API_KEY)).isEqualTo(apiKey);
        assertThat(encryptionService.decrypt(result2.encryptedSecret(), result2.nonce(),
                tenantId, providerId, secretRef, AiCredentialType.API_KEY)).isEqualTo(apiKey);
    }

    @Test
    void 篡改密文导致解密失败() {
        String apiKey = "sk-test-tamper-detection";
        Long tenantId = 1L;
        Long providerId = 10L;
        String secretRef = "uuid-ref-003";

        AiCredentialEncryptionService.EncryptionResult result =
                encryptionService.encrypt(apiKey, tenantId, providerId, secretRef, AiCredentialType.API_KEY);

        // 篡改密文
        byte[] tampered = result.encryptedSecret().clone();
        tampered[0] ^= 0xFF;

        assertThatThrownBy(() -> encryptionService.decrypt(tampered, result.nonce(),
                tenantId, providerId, secretRef, AiCredentialType.API_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("篡改");
    }

    @Test
    void AAD绑定信息不匹配导致解密失败() {
        String apiKey = "sk-test-aad-binding";
        Long tenantId = 1L;
        Long providerId = 10L;
        String secretRef = "uuid-ref-004";

        AiCredentialEncryptionService.EncryptionResult result =
                encryptionService.encrypt(apiKey, tenantId, providerId, secretRef, AiCredentialType.API_KEY);

        // 用错误的 tenantId 解密（AAD 不匹配）
        assertThatThrownBy(() -> encryptionService.decrypt(result.encryptedSecret(), result.nonce(),
                999L, providerId, secretRef, AiCredentialType.API_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("篡改");

        // 用错误的 providerId 解密
        assertThatThrownBy(() -> encryptionService.decrypt(result.encryptedSecret(), result.nonce(),
                tenantId, 999L, secretRef, AiCredentialType.API_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("篡改");

        // 用错误的 secretReference 解密
        assertThatThrownBy(() -> encryptionService.decrypt(result.encryptedSecret(), result.nonce(),
                tenantId, providerId, "wrong-ref", AiCredentialType.API_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("篡改");
    }

    @Test
    void HMAC指纹一致性验证() {
        String apiKey = "sk-test-fingerprint";
        Long tenantId = 1L;
        Long providerId = 10L;

        String fp1 = fingerprintService.computeFingerprint(apiKey, tenantId, providerId, AiCredentialType.API_KEY);
        String fp2 = fingerprintService.computeFingerprint(apiKey, tenantId, providerId, AiCredentialType.API_KEY);

        // 相同输入产生相同指纹
        assertThat(fp1).isEqualTo(fp2);
        // 指纹为 64 字符十六进制字符串（SHA256 = 32 字节 = 64 hex chars）
        assertThat(fp1).hasSize(64);
        assertThat(fp1).matches("[0-9a-f]+");
    }

    @Test
    void 不同租户相同Key产生不同指纹() {
        String apiKey = "sk-test-cross-tenant-fingerprint";
        Long providerId = 10L;

        String fpTenant1 = fingerprintService.computeFingerprint(apiKey, 1L, providerId, AiCredentialType.API_KEY);
        String fpTenant2 = fingerprintService.computeFingerprint(apiKey, 2L, providerId, AiCredentialType.API_KEY);

        // 不同租户的指纹不同
        assertThat(fpTenant1).isNotEqualTo(fpTenant2);
    }

    @Test
    void 不同Key产生不同指纹() {
        Long tenantId = 1L;
        Long providerId = 10L;

        String fp1 = fingerprintService.computeFingerprint("sk-test-key-1", tenantId, providerId, AiCredentialType.API_KEY);
        String fp2 = fingerprintService.computeFingerprint("sk-test-key-2", tenantId, providerId, AiCredentialType.API_KEY);

        assertThat(fp1).isNotEqualTo(fp2);
    }

    @Test
    void 加密密钥和指纹密钥相同导致初始化失败() {
        AiCredentialEncryptionProperties props = new AiCredentialEncryptionProperties();
        props.setActiveKeyId("test-key-v1");
        String sameKey = Base64.getEncoder().encodeToString(TEST_ENCRYPTION_KEY);
        props.setActiveKeyBase64(sameKey);
        props.setFingerprintKeyBase64(sameKey);

        assertThatThrownBy(props::validateAndInit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能相同");
    }

    @Test
    void 密钥长度不正确导致初始化失败() {
        AiCredentialEncryptionProperties props = new AiCredentialEncryptionProperties();
        props.setActiveKeyId("test-key-v1");
        // 16 字节不是 32 字节
        props.setActiveKeyBase64(Base64.getEncoder().encodeToString(new byte[16]));
        props.setFingerprintKeyBase64(Base64.getEncoder().encodeToString(TEST_FINGERPRINT_KEY));

        assertThatThrownBy(props::validateAndInit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("加密主密钥长度");
    }
}
