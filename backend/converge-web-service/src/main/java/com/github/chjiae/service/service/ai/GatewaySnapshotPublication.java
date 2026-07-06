package com.github.chjiae.service.service.ai;

import com.github.chjiae.contract.gateway.GatewaySnapshotChangedEvent;
import com.github.chjiae.contract.gateway.GatewaySnapshotManifest;

/**
 * 待写入 Redis 的网关快照发布包。
 *
 * @param tenantId 租户 ID
 * @param revision 快照 revision
 * @param payloadRedisKey payload key
 * @param payloadJson payload JSON
 * @param manifest manifest
 * @param changedEvent Pub/Sub 变更提示事件
 */
public record GatewaySnapshotPublication(
        String tenantId,
        long revision,
        String payloadRedisKey,
        String payloadJson,
        GatewaySnapshotManifest manifest,
        GatewaySnapshotChangedEvent changedEvent
) {
}
