package com.github.chjiae.contract.gateway;

/**
 * 网关快照中的资源池成员。
 *
 * @param tenantId 租户 ID
 * @param poolId 资源池 ID
 * @param executionResourceId 执行资源 ID
 * @param adminStatus 管理状态
 * @param priority 成员优先级，数值越大越优先
 * @param weight 同优先级内的正整数权重
 */
public record GatewayResourcePoolMemberSnapshot(
        String tenantId,
        String poolId,
        String executionResourceId,
        String adminStatus,
        int priority,
        int weight
) {
}
