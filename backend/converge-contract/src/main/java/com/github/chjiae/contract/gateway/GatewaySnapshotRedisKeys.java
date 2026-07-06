package com.github.chjiae.contract.gateway;

/**
 * 网关快照 Redis key 约定。
 */
public final class GatewaySnapshotRedisKeys {

    /** Redis key 前缀。 */
    private static final String PREFIX = "converge:gateway:snapshot";

    private GatewaySnapshotRedisKeys() {
    }

    /**
     * 租户索引 key。
     *
     * @return tenant index key
     */
    public static String tenantIndexKey() {
        return PREFIX + ":tenant-index";
    }

    /**
     * 租户不可变 payload key。
     *
     * @param tenantId 租户 ID
     * @param revision revision
     * @return payload key
     */
    public static String payloadKey(String tenantId, long revision) {
        return PREFIX + ":tenant:" + tenantId + ":revision:" + revision;
    }

    /**
     * 租户 current manifest key。
     *
     * @param tenantId 租户 ID
     * @return current manifest key
     */
    public static String currentManifestKey(String tenantId) {
        return PREFIX + ":tenant:" + tenantId + ":current";
    }

    /**
     * 租户历史 key。
     *
     * @param tenantId 租户 ID
     * @return history key
     */
    public static String historyKey(String tenantId) {
        return PREFIX + ":tenant:" + tenantId + ":history";
    }

    /**
     * 快照变更提示频道。
     *
     * @return Pub/Sub 频道
     */
    public static String changedChannel() {
        return PREFIX + ":changed";
    }
}
