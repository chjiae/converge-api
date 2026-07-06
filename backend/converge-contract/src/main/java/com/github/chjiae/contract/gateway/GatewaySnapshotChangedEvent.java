package com.github.chjiae.contract.gateway;

/**
 * 网关快照变更提示事件。
 * Pub/Sub 消息只作为刷新提示，网关必须重新读取 Redis Manifest 与 payload。
 *
 * @param schemaVersion schema 版本
 * @param tenantId 租户 ID
 * @param revision 提示的 revision
 * @param manifestRedisKey 当前 Manifest Redis key
 * @param publishedAtEpochMillis 发布时间，Unix 毫秒
 */
public record GatewaySnapshotChangedEvent(
        int schemaVersion,
        String tenantId,
        long revision,
        String manifestRedisKey,
        long publishedAtEpochMillis
) {
}
