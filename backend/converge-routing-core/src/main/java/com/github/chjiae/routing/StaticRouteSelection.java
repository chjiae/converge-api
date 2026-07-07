package com.github.chjiae.routing;

/**
 * 真实数据面请求的静态路由选择结果。
 * 只包含执行资源与安全路由元数据，不包含 Client Key、上游 secret 或任何动态状态。
 *
 * @param tenantId 租户 ID
 * @param publicModelCode 公开模型编码
 * @param canonicalOperation 规范化操作
 * @param routePolicyId 路由策略 ID
 * @param snapshotRevision 快照 revision
 * @param targetPriority 选中的 RouteTarget 优先级
 * @param resourcePoolId 选中的资源池 ID
 * @param resourcePoolCode 选中的资源池编码
 * @param memberPriority 选中的 PoolMember 优先级
 * @param executionResourceId 选中的执行资源 ID
 * @param upstreamModelName 选中资源的上游模型名
 */
public record StaticRouteSelection(
        String tenantId,
        String publicModelCode,
        String canonicalOperation,
        String routePolicyId,
        long snapshotRevision,
        int targetPriority,
        String resourcePoolId,
        String resourcePoolCode,
        int memberPriority,
        String executionResourceId,
        String upstreamModelName
) {
}
