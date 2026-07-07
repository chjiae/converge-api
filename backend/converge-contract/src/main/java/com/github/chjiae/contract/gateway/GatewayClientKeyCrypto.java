package com.github.chjiae.contract.gateway;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 下游 Client API Key 生成、解析与 verifier 工具。
 * 该类只使用 JDK 标准库，供控制面和网关共同使用，避免 verifier 算法分叉。
 */
public final class GatewayClientKeyCrypto {

    /** Raw key 固定前缀。 */
    public static final String RAW_KEY_PREFIX = "cvg_live_";

    /** verifier 摘要算法。 */
    public static final String HASH_ALGORITHM = "SHA-256";

    /** verifier 固定域分隔符。 */
    private static final String DOMAIN = "CONVERGE_CLIENT_KEY_V1";

    /** keyId 随机字节数。 */
    private static final int KEY_ID_BYTES = 16;

    /** secret 随机字节数。 */
    private static final int SECRET_BYTES = 32;

    /** salt 随机字节数。 */
    public static final int SALT_BYTES = 16;

    /** SHA-256 输出字节数。 */
    public static final int HASH_BYTES = 32;

    /** keyId 固定 Base64URL 字符长度。 */
    private static final int KEY_ID_LENGTH = 22;

    /** secret 固定 Base64URL 字符长度。 */
    private static final int SECRET_LENGTH = 43;

    /** Base64 URL 无填充字符约束。 */
    private static final Pattern BASE64_URL_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    /** 安全随机源。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private GatewayClientKeyCrypto() {
    }

    /**
     * 生成全新的 Client API Key。
     *
     * @return 生成结果
     */
    public static GeneratedClientKey generate() {
        String keyId = randomBase64Url(KEY_ID_BYTES);
        return generateForKeyId(keyId);
    }

    /**
     * 为已有 keyId 生成新的 secret，用于轮换。
     *
     * @param keyId 已有 keyId
     * @return 生成结果
     */
    public static GeneratedClientKey generateForKeyId(String keyId) {
        if (!validTokenPart(keyId) || keyId.length() != KEY_ID_LENGTH) {
            throw new IllegalArgumentException("Client API Key 的 keyId 不合法");
        }
        String secret = randomBase64Url(SECRET_BYTES);
        String rawKey = RAW_KEY_PREFIX + keyId + "_" + secret;
        return new GeneratedClientKey(keyId, secret, rawKey, maskedPreview(keyId));
    }

    /**
     * 生成 verifier salt。
     *
     * @return 16 字节随机 salt
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * 严格解析 raw key。
     *
     * @param rawKey 原始 key
     * @return 解析结果
     */
    public static ParsedClientKey parse(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(RAW_KEY_PREFIX)) {
            return ParsedClientKey.invalid();
        }
        int expectedLength = RAW_KEY_PREFIX.length() + KEY_ID_LENGTH + 1 + SECRET_LENGTH;
        if (rawKey.length() != expectedLength) {
            return ParsedClientKey.invalid();
        }
        int separatorIndex = RAW_KEY_PREFIX.length() + KEY_ID_LENGTH;
        if (rawKey.charAt(separatorIndex) != '_') {
            return ParsedClientKey.invalid();
        }
        String keyId = rawKey.substring(RAW_KEY_PREFIX.length(), separatorIndex);
        String secret = rawKey.substring(separatorIndex + 1);
        if (!validTokenPart(keyId) || !validTokenPart(secret)) {
            return ParsedClientKey.invalid();
        }
        return new ParsedClientKey(true, keyId, secret);
    }

    /**
     * 计算 raw key verifier。
     *
     * @param rawKey 原始 key
     * @param expectedKeyId 期望 keyId
     * @param keyVersion key 版本
     * @param salt verifier salt
     * @return SHA-256 verifier
     */
    public static byte[] verifier(String rawKey, String expectedKeyId, int keyVersion, byte[] salt) {
        ParsedClientKey parsed = parse(rawKey);
        if (!parsed.valid() || !parsed.keyId().equals(expectedKeyId)) {
            throw new IllegalArgumentException("Client API Key 与 keyId 不匹配");
        }
        return verifierFromSecret(parsed.keyId(), keyVersion, salt, parsed.secret());
    }

    /**
     * 常量时间验证 raw key。
     *
     * @param rawKey 原始 key
     * @param expectedKeyId 期望 keyId
     * @param keyVersion key 版本
     * @param salt verifier salt
     * @param expectedHash 期望 hash
     * @return 是否匹配
     */
    public static boolean verify(String rawKey, String expectedKeyId, int keyVersion,
                                 byte[] salt, byte[] expectedHash) {
        try {
            byte[] actual = verifier(rawKey, expectedKeyId, keyVersion, salt);
            return MessageDigest.isEqual(actual, expectedHash);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 构造安全掩码预览。
     *
     * @param keyId keyId
     * @return 掩码预览
     */
    public static String maskedPreview(String keyId) {
        if (keyId == null || keyId.length() < 10) {
            return RAW_KEY_PREFIX + "****";
        }
        return RAW_KEY_PREFIX + keyId.substring(0, 6) + "..." + keyId.substring(keyId.length() - 4);
    }

    private static byte[] verifierFromSecret(String keyId, int keyVersion, byte[] salt, String secret) {
        if (salt == null || salt.length != SALT_BYTES) {
            throw new IllegalArgumentException("Client API Key verifier salt 长度不合法");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(fixedBinaryEncoding(keyId, keyVersion, salt, secret));
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private static byte[] fixedBinaryEncoding(String keyId, int keyVersion, byte[] salt, String secret) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeUtf8(output, DOMAIN);
            writeUtf8(output, keyId);
            output.writeInt(keyVersion);
            output.writeInt(salt.length);
            output.write(salt);
            writeUtf8(output, secret);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Client API Key verifier 编码失败", e);
        }
    }

    private static void writeUtf8(DataOutputStream output, String value) throws IOException {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(valueBytes.length);
        output.write(valueBytes);
    }

    private static String randomBase64Url(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean validTokenPart(String value) {
        return value != null
                && !value.isBlank()
                && BASE64_URL_PATTERN.matcher(value).matches();
    }

    /**
     * 生成的 Client API Key。
     *
     * @param keyId keyId
     * @param secret secret 部分
     * @param rawKey 一次性返回的完整 raw key
     * @param maskedPreview 掩码预览
     */
    public record GeneratedClientKey(String keyId, String secret, String rawKey, String maskedPreview) {
    }

    /**
     * Raw key 解析结果。
     *
     * @param valid 是否合法
     * @param keyId keyId
     * @param secret secret 部分
     */
    public record ParsedClientKey(boolean valid, String keyId, String secret) {

        private static ParsedClientKey invalid() {
            return new ParsedClientKey(false, null, null);
        }
    }
}
