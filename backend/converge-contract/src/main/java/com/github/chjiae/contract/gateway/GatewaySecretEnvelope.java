package com.github.chjiae.contract.gateway;

/**
 * 网关投递秘密信封。
 *
 * @param keyId 网关投递密钥版本标识，必须与 Manifest 中的 gatewayKeyId 一致
 * @param algorithm 秘密封装算法，当前固定为 AES-256-GCM
 * @param nonceBase64 AES-GCM 12 字节随机 Nonce 的 Base64 表示
 * @param ciphertextBase64 AES-GCM 密文和认证标签的 Base64 表示
 */
public record GatewaySecretEnvelope(
        String keyId,
        String algorithm,
        String nonceBase64,
        String ciphertextBase64
) {
}
