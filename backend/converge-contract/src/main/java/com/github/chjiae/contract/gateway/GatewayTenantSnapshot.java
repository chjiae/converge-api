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
 */
public record GatewayTenantSnapshot(
        int schemaVersion,
        String tenantId,
        long revision,
        long generatedAtEpochMillis,
        List<GatewayPublicModelSnapshot> publicModels,
        List<GatewayExecutionResourceSnapshot> executionResources
) {

    /**
     * 复制集合字段，确保契约对象不可变。
     */
    public GatewayTenantSnapshot {
        publicModels = List.copyOf(publicModels == null ? List.of() : publicModels);
        executionResources = List.copyOf(executionResources == null ? List.of() : executionResources);
    }
}
