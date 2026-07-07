package com.github.chjiae.contract.gateway;

/**
 * 访问组模型授权快照。
 * 授权粒度固定为 PublicModel + CanonicalOperation 的精确组合。
 *
 * @param tenantId 租户 ID
 * @param grantId 授权 ID
 * @param accessGroupId 访问组 ID
 * @param publicModelId 公开模型 ID
 * @param publicModelCode 公开模型编码
 * @param canonicalOperation 规范化操作类型
 * @param adminStatus 管理状态
 */
public record GatewayAccessGroupModelGrantSnapshot(
        String tenantId,
        String grantId,
        String accessGroupId,
        String publicModelId,
        String publicModelCode,
        String canonicalOperation,
        String adminStatus
) {
}
