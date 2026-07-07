package com.github.chjiae.contract.gateway;

import java.util.List;

/**
 * 网关快照中的上游资源池。
 *
 * @param tenantId 租户 ID
 * @param poolId 资源池 ID
 * @param poolCode 资源池编码
 * @param displayName 展示名称
 * @param adminStatus 管理状态
 * @param selectionPolicy 选择策略，本阶段固定为 PRIORITY_WEIGHTED
 * @param members 池内成员
 */
public record GatewayResourcePoolSnapshot(
        String tenantId,
        String poolId,
        String poolCode,
        String displayName,
        String adminStatus,
        String selectionPolicy,
        List<GatewayResourcePoolMemberSnapshot> members
) {

    /**
     * 复制成员集合，确保契约对象不可变。
     */
    public GatewayResourcePoolSnapshot {
        members = List.copyOf(members == null ? List.of() : members);
    }
}
