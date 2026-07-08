package com.github.chjiae.gateway.snapshot;

import com.github.chjiae.contract.gateway.GatewaySnapshotSyncState;

import java.util.List;

/**
 * 网关快照运行时安全状态。
 *
 * @param state 同步状态
 * @param indexTenantCount Redis tenant index 中的租户数量
 * @param tenantIndexCount 本地 tenant key index 数量
 * @param loadedTenantCount 本地已加载租户数量
 * @param compiledRoutePlanCount 已编译静态路由计划数量
 * @param clientKeyCount 已加载 Client API Key 索引数量
 * @param loadedClientKeyCount 已加载 Client API Key 数量
 * @param loadedAccessGroupCount 已加载访问组数量
 * @param loadedGrantCount 已加载授权数量
 * @param loadedRoutePlanCount 已加载路由计划数量
 * @param loadedRuntimePolicyCount 已加载运行时策略数量
 * @param invalidRouteTenantCount 最近存在静态路由错误的租户数量
 * @param snapshotRefreshTotalCount 快照刷新总次数
 * @param snapshotRefreshSkippedCount revision skip 次数
 * @param snapshotRefreshFailedCount 快照刷新失败次数
 * @param tenantRefreshInFlightCount tenant refresh 运行中数量
 * @param tenantRefreshPendingCount tenant refresh pending 数量
 * @param lastTenantRefreshEpochMillis 最近 tenant refresh 时间
 * @param lastFullReconcileEpochMillis 最近 full reconcile 时间
 * @param lastRefreshDurationMs 最近刷新耗时
 * @param maxRefreshDurationMs 最大刷新耗时
 * @param estimatedSnapshotPayloadBytes 已加载 payload 估算字节数
 * @param lastSuccessfulReconcileEpochMillis 最近成功对账时间
 * @param latestErrorCategory 最近错误分类
 * @param tenants 本地租户 revision 摘要
 */
public record GatewaySnapshotRuntimeStatus(
        GatewaySnapshotSyncState state,
        int indexTenantCount,
        int tenantIndexCount,
        int loadedTenantCount,
        int compiledRoutePlanCount,
        int clientKeyCount,
        int loadedClientKeyCount,
        int loadedAccessGroupCount,
        int loadedGrantCount,
        int loadedRoutePlanCount,
        int loadedRuntimePolicyCount,
        int invalidRouteTenantCount,
        long snapshotRefreshTotalCount,
        long snapshotRefreshSkippedCount,
        long snapshotRefreshFailedCount,
        int tenantRefreshInFlightCount,
        int tenantRefreshPendingCount,
        long lastTenantRefreshEpochMillis,
        long lastFullReconcileEpochMillis,
        long lastRefreshDurationMs,
        long maxRefreshDurationMs,
        long estimatedSnapshotPayloadBytes,
        long lastSuccessfulReconcileEpochMillis,
        String latestErrorCategory,
        List<TenantRevision> tenants
) {

    /**
     * 租户 revision 摘要。
     *
     * @param tenantId 租户 ID
     * @param revision 本地 revision
     * @param schemaVersion 快照 schema 版本
     * @param routePlanCount 已编译计划数量
     * @param clientKeyCount 租户 Client API Key 数量
     */
    public record TenantRevision(String tenantId, long revision, int schemaVersion,
                                 int routePlanCount, int clientKeyCount) {
    }
}
