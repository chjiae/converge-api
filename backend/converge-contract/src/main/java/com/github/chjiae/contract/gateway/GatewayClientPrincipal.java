package com.github.chjiae.contract.gateway;

import java.util.List;

/**
 * 网关内存中的下游调用方主体。
 * 该对象不包含 raw key，只包含已验证后的租户、Key 与有效授权摘要。
 *
 * @param tenantId 租户 ID
 * @param clientApiKeyId Client API Key ID
 * @param keyId keyId
 * @param accessGroupIds 已启用访问组 ID
 * @param effectiveGrants 生效的授权并集
 */
public record GatewayClientPrincipal(
        String tenantId,
        String clientApiKeyId,
        String keyId,
        List<String> accessGroupIds,
        List<GatewayAccessGroupModelGrantSnapshot> effectiveGrants
) {

    /**
     * 复制集合字段，避免调用方修改主体授权。
     */
    public GatewayClientPrincipal {
        accessGroupIds = List.copyOf(accessGroupIds == null ? List.of() : accessGroupIds);
        effectiveGrants = List.copyOf(effectiveGrants == null ? List.of() : effectiveGrants);
    }
}
