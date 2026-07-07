package com.github.chjiae.service.service.ai;

import lombok.Data;

/**
 * 网关快照资源模型绑定查询行。
 */
@Data
public class GatewaySnapshotModelBindingRow {

    /** 租户 ID */
    private Long tenantId;

    /** 执行资源 ID */
    private Long executionResourceId;

    /** 公开模型 ID */
    private Long publicModelId;

    /** 规范化操作类型 */
    private String canonicalOperation;

    /** 精确上游模型名称 */
    private String upstreamModelName;

    /** 管理状态 */
    private String adminStatus;
}
