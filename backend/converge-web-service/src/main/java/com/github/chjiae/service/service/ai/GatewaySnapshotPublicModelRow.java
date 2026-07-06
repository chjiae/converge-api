package com.github.chjiae.service.service.ai;

import lombok.Data;

/**
 * 网关快照公开模型查询行。
 */
@Data
public class GatewaySnapshotPublicModelRow {

    /** 租户 ID */
    private Long tenantId;

    /** 公开模型 ID */
    private Long modelId;

    /** 公开模型编码 */
    private String code;

    /** 展示名称 */
    private String displayName;

    /** 模型家族 */
    private String modelFamily;
}
