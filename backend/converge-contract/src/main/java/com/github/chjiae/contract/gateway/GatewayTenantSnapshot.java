package com.github.chjiae.contract.gateway;

import java.util.List;

/**
 * 租户级不可变网关快照。
 *
 * @param schemaVersion 快照 schema 版本
 * @param tenantId 租户 ID
 * @param revision 租户快照 revision，单调递增
 * @param generatedAtEpochMillis 快照生成时间，Unix 毫秒
 * @param publicModels 已启用公开模型目录
 * @param executionResources 可投影执行资源目录
 * @param resourcePools V2 资源池与成员拓扑
 * @param resourceModelBindings V2 资源模型精确绑定
 * @param routePolicies V2 静态路由策略与目标池
 */
public record GatewayTenantSnapshot(
        int schemaVersion,
        String tenantId,
        long revision,
        long generatedAtEpochMillis,
        List<GatewayPublicModelSnapshot> publicModels,
        List<GatewayExecutionResourceSnapshot> executionResources,
        List<GatewayResourcePoolSnapshot> resourcePools,
        List<GatewayResourceModelBindingSnapshot> resourceModelBindings,
        List<GatewayRoutePolicySnapshot> routePolicies
) {

    /**
     * 复制集合字段，确保契约对象不可变。
     */
    public GatewayTenantSnapshot {
        publicModels = List.copyOf(publicModels == null ? List.of() : publicModels);
        executionResources = List.copyOf(executionResources == null ? List.of() : executionResources);
        resourcePools = List.copyOf(resourcePools == null ? List.of() : resourcePools);
        resourceModelBindings = List.copyOf(resourceModelBindings == null ? List.of() : resourceModelBindings);
        routePolicies = List.copyOf(routePolicies == null ? List.of() : routePolicies);
    }

    /**
     * V1 兼容构造器。
     * 阶段 04 仅包含公开模型和执行资源，V2 新字段在读取 V1 payload 时保持空集合。
     */
    public GatewayTenantSnapshot(int schemaVersion, String tenantId, long revision,
                                 long generatedAtEpochMillis,
                                 List<GatewayPublicModelSnapshot> publicModels,
                                 List<GatewayExecutionResourceSnapshot> executionResources) {
        this(schemaVersion, tenantId, revision, generatedAtEpochMillis,
                publicModels, executionResources, List.of(), List.of(), List.of());
    }
}
