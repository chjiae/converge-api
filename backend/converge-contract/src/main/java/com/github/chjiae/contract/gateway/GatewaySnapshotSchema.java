package com.github.chjiae.contract.gateway;

/**
 * 网关快照协议固定常量。
 * 控制面和网关必须共同使用这些常量，避免 schema、摘要、签名或秘密封装算法分叉。
 */
public final class GatewaySnapshotSchema {

    /** 阶段 04 快照 schema 版本。 */
    public static final int VERSION_1 = 1;

    /** 阶段 05 静态路由快照 schema 版本。 */
    public static final int VERSION_2 = 2;

    /** 当前控制面发布的快照 schema 版本。 */
    public static final int CURRENT_VERSION = VERSION_2;

    /** 网关当前支持的最小快照 schema 版本。 */
    public static final int MIN_SUPPORTED_VERSION = VERSION_1;

    /** 网关当前支持的最大快照 schema 版本。 */
    public static final int MAX_SUPPORTED_VERSION = VERSION_2;

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
