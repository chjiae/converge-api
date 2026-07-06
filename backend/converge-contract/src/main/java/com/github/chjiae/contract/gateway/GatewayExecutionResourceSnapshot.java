package com.github.chjiae.contract.gateway;

/**
 * 网关可执行资源快照。
 * 本阶段只表达 Direct API 静态资源，不表达路由、权重、限流或模型绑定。
 *
 * @param tenantId 所属租户 ID
 * @param resourceId 可执行资源 ID
 * @param providerId Provider ID
 * @param connectionId 上游连接 ID
 * @param credentialId 凭据 ID
 * @param resourceType 资源类型，当前为 DIRECT_API
 * @param adminStatus 管理状态，ENABLED 或 DRAINING 可投影
 * @param providerKind Provider 类型
 * @param protocolType 上游协议类型
 * @param baseUrl 规范化后的上游 Base URL
 * @param secretEnvelope 网关投递秘密信封
 */
public record GatewayExecutionResourceSnapshot(
        String tenantId,
        String resourceId,
        String providerId,
        String connectionId,
        String credentialId,
        String resourceType,
        String adminStatus,
        String providerKind,
        String protocolType,
        String baseUrl,
        GatewaySecretEnvelope secretEnvelope
) {
}
