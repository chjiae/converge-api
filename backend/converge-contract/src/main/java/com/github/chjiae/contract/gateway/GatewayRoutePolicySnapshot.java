package com.github.chjiae.contract.gateway;

import java.util.List;

/**
 * 网关快照中的静态路由策略。
 *
 * @param tenantId 租户 ID
 * @param policyId 策略 ID
 * @param publicModelId 公开模型 ID
 * @param publicModelCode 公开模型编码
 * @param canonicalOperation 规范化操作类型
 * @param adminStatus 管理状态
 * @param selectionPolicy 选择策略，本阶段固定为 PRIORITY_WEIGHTED
 * @param targets 路由目标池
 */
public record GatewayRoutePolicySnapshot(
        String tenantId,
        String policyId,
        String publicModelId,
        String publicModelCode,
        String canonicalOperation,
        String adminStatus,
        String selectionPolicy,
        List<GatewayRouteTargetSnapshot> targets
) {

    /**
     * 复制目标集合，确保契约对象不可变。
     */
    public GatewayRoutePolicySnapshot {
        targets = List.copyOf(targets == null ? List.of() : targets);
    }
}
