package com.github.chjiae.contract.gateway;

/**
 * 网关快照中的静态路由目标池。
 *
 * @param tenantId 租户 ID
 * @param routePolicyId 路由策略 ID
 * @param resourcePoolId 资源池 ID
 * @param adminStatus 管理状态
 * @param priority 目标优先级，数值越大越优先
 * @param weight 同优先级内的正整数权重
 */
public record GatewayRouteTargetSnapshot(
        String tenantId,
        String routePolicyId,
        String resourcePoolId,
        String adminStatus,
        int priority,
        int weight
) {
}
