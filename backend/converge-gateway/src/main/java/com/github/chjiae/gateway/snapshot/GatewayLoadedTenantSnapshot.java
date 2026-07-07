package com.github.chjiae.gateway.snapshot;

import com.github.chjiae.contract.gateway.GatewayTenantSnapshot;
import com.github.chjiae.routing.StaticRoutePlan;

import java.util.Map;

/**
 * 网关已加载租户快照。
 * runtimeSecrets 只存在于内存，不通过内部状态接口输出。
 *
 * @param snapshot 契约快照
 * @param runtimeSecrets 执行资源 ID 到明文运行时秘密的映射
 * @param routePlans 静态路由计划，key 为 publicModelCode + operation
 */
record GatewayLoadedTenantSnapshot(
        GatewayTenantSnapshot snapshot,
        Map<String, String> runtimeSecrets,
        Map<String, StaticRoutePlan> routePlans
) {

    /**
     * 复制明文秘密与路由计划映射，避免调用方后续修改。
     */
    GatewayLoadedTenantSnapshot {
        runtimeSecrets = Map.copyOf(runtimeSecrets == null ? Map.of() : runtimeSecrets);
        routePlans = Map.copyOf(routePlans == null ? Map.of() : routePlans);
    }
}
