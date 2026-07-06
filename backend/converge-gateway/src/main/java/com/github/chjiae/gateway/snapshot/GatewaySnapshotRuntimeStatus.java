package com.github.chjiae.gateway.snapshot;

import com.github.chjiae.contract.gateway.GatewaySnapshotSyncState;

import java.util.List;

/**
 * 网关快照运行时安全状态。
 *
 * @param state 同步状态
 * @param indexTenantCount Redis tenant index 中的租户数量
 * @param loadedTenantCount 本地已加载租户数量
 * @param lastSuccessfulReconcileEpochMillis 最近成功对账时间
 * @param latestErrorCategory 最近错误分类
 * @param tenants 本地租户 revision 摘要
 */
public record GatewaySnapshotRuntimeStatus(
        GatewaySnapshotSyncState state,
        int indexTenantCount,
        int loadedTenantCount,
        long lastSuccessfulReconcileEpochMillis,
        String latestErrorCategory,
        List<TenantRevision> tenants
) {

    /**
     * 租户 revision 摘要。
     *
     * @param tenantId 租户 ID
     * @param revision 本地 revision
     */
    public record TenantRevision(String tenantId, long revision) {
    }
}
