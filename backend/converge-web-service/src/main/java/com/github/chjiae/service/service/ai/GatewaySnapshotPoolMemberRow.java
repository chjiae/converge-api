package com.github.chjiae.service.service.ai;

import lombok.Data;

/**
 * 网关快照资源池成员查询行。
 */
@Data
public class GatewaySnapshotPoolMemberRow {

    /** 租户 ID */
    private Long tenantId;

    /** 资源池 ID */
    private Long poolId;

    /** 执行资源 ID */
    private Long executionResourceId;

    /** 管理状态 */
    private String adminStatus;

    /** 成员优先级 */
    private Integer priority;

    /** 成员权重 */
    private Integer weight;
}
