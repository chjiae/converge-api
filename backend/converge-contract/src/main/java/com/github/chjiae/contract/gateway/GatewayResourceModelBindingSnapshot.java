package com.github.chjiae.contract.gateway;

/**
 * 网关快照中的资源模型精确绑定。
 *
 * @param tenantId 租户 ID
 * @param executionResourceId 执行资源 ID
 * @param publicModelId 公开模型 ID
 * @param canonicalOperation 规范化操作类型
 * @param upstreamModelName 精确上游模型名称
 * @param adminStatus 管理状态
 */
public record GatewayResourceModelBindingSnapshot(
        String tenantId,
        String executionResourceId,
        String publicModelId,
        String canonicalOperation,
        String upstreamModelName,
        String adminStatus
) {
}
