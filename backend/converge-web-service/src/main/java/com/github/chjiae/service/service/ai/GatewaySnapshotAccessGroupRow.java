package com.github.chjiae.service.service.ai;

import lombok.Data;

/**
 * 网关快照访问组查询行。
 */
@Data
public class GatewaySnapshotAccessGroupRow {

    /** 租户 ID */
    private Long tenantId;

    /** 访问组 ID */
    private Long accessGroupId;

    /** 访问组编码 */
    private String code;

    /** 管理状态 */
    private String adminStatus;
}
