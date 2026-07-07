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
 * @param accessGroups V3 访问组
 * @param accessGroupModelGrants V3 访问组模型授权
 * @param clientApiKeys V3 下游 Client API Key verifier 元数据
 * @param clientApiKeyAccessGroups V3 Client API Key 与访问组绑定
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
        List<GatewayRoutePolicySnapshot> routePolicies,
        List<GatewayAccessGroupSnapshot> accessGroups,
        List<GatewayAccessGroupModelGrantSnapshot> accessGroupModelGrants,
        List<GatewayClientApiKeySnapshot> clientApiKeys,
        List<GatewayClientApiKeyAccessGroupSnapshot> clientApiKeyAccessGroups
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
        accessGroups = List.copyOf(accessGroups == null ? List.of() : accessGroups);
        accessGroupModelGrants = List.copyOf(accessGroupModelGrants == null ? List.of() : accessGroupModelGrants);
        clientApiKeys = List.copyOf(clientApiKeys == null ? List.of() : clientApiKeys);
        clientApiKeyAccessGroups = List.copyOf(clientApiKeyAccessGroups == null ? List.of() : clientApiKeyAccessGroups);
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
                publicModels, executionResources, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    /**
     * V2 兼容构造器。
     * 阶段 05 包含静态路由拓扑，V3 访问授权字段保持空集合。
     */
    public GatewayTenantSnapshot(int schemaVersion, String tenantId, long revision,
                                 long generatedAtEpochMillis,
                                 List<GatewayPublicModelSnapshot> publicModels,
                                 List<GatewayExecutionResourceSnapshot> executionResources,
                                 List<GatewayResourcePoolSnapshot> resourcePools,
                                 List<GatewayResourceModelBindingSnapshot> resourceModelBindings,
                                 List<GatewayRoutePolicySnapshot> routePolicies) {
        this(schemaVersion, tenantId, revision, generatedAtEpochMillis,
                publicModels, executionResources, resourcePools, resourceModelBindings, routePolicies,
                List.of(), List.of(), List.of(), List.of());
    }
}
