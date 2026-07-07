package com.github.chjiae.routing;

import java.util.List;

/**
 * 不可变静态路由计划。
 * 该对象只包含路由安全元数据和资源 ID，不包含 API Key、密文、nonce、Redis key 或 HMAC。
 *
 * @param tenantId 租户 ID
 * @param publicModelCode 公开模型编码
 * @param canonicalOperation 规范化操作类型
 * @param policyId 路由策略 ID
 * @param snapshotRevision 快照 revision
 * @param validationStatus 校验状态
 * @param routeTargetTiers 目标池优先级层
 */
public record StaticRoutePlan(
        String tenantId,
        String publicModelCode,
        String canonicalOperation,
        String policyId,
        long snapshotRevision,
        String validationStatus,
        List<StaticRouteTargetTier> routeTargetTiers
) {

    /**
     * 复制集合，保持不可变。
     */
    public StaticRoutePlan {
        routeTargetTiers = List.copyOf(routeTargetTiers == null ? List.of() : routeTargetTiers);
    }
}
