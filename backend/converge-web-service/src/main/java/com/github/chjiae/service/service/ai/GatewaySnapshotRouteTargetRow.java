package com.github.chjiae.service.service.ai;

import lombok.Data;

/**
 * 网关快照路由目标查询行。
 */
@Data
public class GatewaySnapshotRouteTargetRow {

    /** 租户 ID */
    private Long tenantId;

    /** 策略 ID */
    private Long policyId;

    /** 资源池 ID */
    private Long poolId;

    /** 管理状态 */
    private String adminStatus;

    /** 目标优先级 */
    private Integer priority;

    /** 目标权重 */
    private Integer weight;
}
