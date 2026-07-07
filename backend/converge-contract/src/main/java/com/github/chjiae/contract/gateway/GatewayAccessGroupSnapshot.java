package com.github.chjiae.contract.gateway;

/**
 * 网关访问组快照。
 * 只包含认证授权所需的不可变安全元数据，不包含任何秘密。
 *
 * @param tenantId 租户 ID
 * @param accessGroupId 访问组 ID
 * @param code 访问组编码
 * @param adminStatus 管理状态
 */
public record GatewayAccessGroupSnapshot(
        String tenantId,
        String accessGroupId,
        String code,
        String adminStatus
) {
}
