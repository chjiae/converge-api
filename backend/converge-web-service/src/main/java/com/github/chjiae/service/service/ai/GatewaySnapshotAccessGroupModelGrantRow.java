package com.github.chjiae.service.service.ai;

import lombok.Data;

/**
 * 网关快照访问组模型授权查询行。
 */
@Data
public class GatewaySnapshotAccessGroupModelGrantRow {

    /** 租户 ID */
    private Long tenantId;

    /** 授权 ID */
    private Long grantId;

    /** 访问组 ID */
    private Long accessGroupId;

    /** 公开模型 ID */
    private Long publicModelId;

    /** 公开模型编码 */
    private String publicModelCode;

    /** 规范化操作类型 */
    private String canonicalOperation;

    /** 管理状态 */
    private String adminStatus;
}
