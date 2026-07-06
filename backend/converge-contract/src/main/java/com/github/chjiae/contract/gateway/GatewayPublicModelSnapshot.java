package com.github.chjiae.contract.gateway;

/**
 * 网关公开模型目录快照。
 *
 * @param tenantId 所属租户 ID
 * @param modelId 控制面公开模型 ID
 * @param code 下游可见的稳定模型别名
 * @param displayName 模型展示名称
 * @param modelFamily 模型家族
 */
public record GatewayPublicModelSnapshot(
        String tenantId,
        String modelId,
        String code,
        String displayName,
        String modelFamily
) {
}
