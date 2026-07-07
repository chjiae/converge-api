package com.github.chjiae.contract.gateway;

/**
 * Client API Key 与访问组绑定快照。
 *
 * @param tenantId 租户 ID
 * @param bindingId 绑定 ID
 * @param clientApiKeyId Client API Key ID
 * @param accessGroupId 访问组 ID
 * @param adminStatus 管理状态
 */
public record GatewayClientApiKeyAccessGroupSnapshot(
        String tenantId,
        String bindingId,
        String clientApiKeyId,
        String accessGroupId,
        String adminStatus
) {
}
