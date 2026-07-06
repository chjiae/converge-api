package com.github.chjiae.contract.gateway;

/**
 * 网关快照协议固定常量。
 * 控制面和网关必须共同使用这些常量，避免 schema、摘要、签名或秘密封装算法分叉。
 */
public final class GatewaySnapshotSchema {

    /** 当前支持的快照 schema 版本。 */
    public static final int CURRENT_VERSION = 1;

    /** 秘密 envelope 的算法标识。 */
    public static final String SECRET_ALGORITHM = "AES-256-GCM";

    /** Manifest HMAC 签名算法。 */
    public static final String MANIFEST_HMAC_ALGORITHM = "HmacSHA256";

    /** AES-GCM 算法名称。 */
    public static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";

    /** AES-GCM 推荐随机 Nonce 长度。 */
    public static final int GCM_NONCE_LENGTH_BYTES = 12;

    /** AES-GCM 认证标签长度。 */
    public static final int GCM_TAG_LENGTH_BITS = 128;

    private GatewaySnapshotSchema() {
    }
}
