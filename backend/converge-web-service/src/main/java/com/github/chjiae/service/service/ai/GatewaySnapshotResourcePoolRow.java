package com.github.chjiae.service.service.ai;

import lombok.Data;

/**
 * 网关快照资源池查询行。
 */
@Data
public class GatewaySnapshotResourcePoolRow {

    /** 租户 ID */
    private Long tenantId;

    /** 资源池 ID */
    private Long poolId;

    /** 资源池编码 */
    private String poolCode;

    /** 展示名称 */
    private String displayName;

    /** 管理状态 */
    private String adminStatus;

    /** 选择策略 */
    private String selectionPolicy;
}
