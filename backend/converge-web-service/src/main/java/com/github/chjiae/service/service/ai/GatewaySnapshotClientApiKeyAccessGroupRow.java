package com.github.chjiae.service.service.ai;

import lombok.Data;

/**
 * 网关快照 Client API Key 访问组绑定查询行。
 */
@Data
public class GatewaySnapshotClientApiKeyAccessGroupRow {

    /** 租户 ID */
    private Long tenantId;

    /** 绑定 ID */
    private Long bindingId;

    /** Client API Key ID */
    private Long clientApiKeyId;

    /** 访问组 ID */
    private Long accessGroupId;

    /** 管理状态 */
    private String adminStatus;
}
