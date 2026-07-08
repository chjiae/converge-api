package com.github.chjiae.routing;

/**
 * 真实请求可尝试的静态路由候选。
 * 该对象只包含路由安全元数据和资源 ID，不包含 API Key、URL、密钥或动态健康状态。
 *
 * @param tenantId 租户 ID
 * @param publicModelCode 公开模型编码
 * @param canonicalOperation 规范化操作类型
 * @param policyId 路由策略 ID
 * @param snapshotRevision 快照 revision
 * @param targetPriority RouteTarget 优先级
 * @param poolId 资源池 ID
 * @param poolCode 资源池编码
 * @param poolWeight 资源池权重
 * @param memberPriority 池成员优先级
 * @param executionResourceId 执行资源 ID
 * @param resourceWeight 执行资源权重
 * @param upstreamModelName 上游模型名称，仅供网关内部改写请求体使用
 */
public record StaticRouteCandidate(
        String tenantId,
        String publicModelCode,
        String canonicalOperation,
        String policyId,
        long snapshotRevision,
        int targetPriority,
        String poolId,
        String poolCode,
        int poolWeight,
        int memberPriority,
        String executionResourceId,
        int resourceWeight,
        String upstreamModelName
) {
}
