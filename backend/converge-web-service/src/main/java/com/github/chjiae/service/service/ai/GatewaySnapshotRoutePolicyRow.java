package com.github.chjiae.service.service.ai;

import lombok.Data;

/**
 * 网关快照路由策略查询行。
 */
@Data
public class GatewaySnapshotRoutePolicyRow {

    /** 租户 ID */
    private Long tenantId;

    /** 策略 ID */
    private Long policyId;

    /** 公开模型 ID */
    private Long publicModelId;

    /** 公开模型编码 */
    private String publicModelCode;

    /** 规范化操作类型 */
    private String canonicalOperation;

    /** 管理状态 */
    private String adminStatus;

    /** 选择策略 */
    private String selectionPolicy;
}
