package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiProtocolType;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 上游连接实体，对应 ai_upstream_connection 表。
 * 保存供应商连接的非敏感地址与协议元数据，不保存任何密钥或账号信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_upstream_connection")
public class AiUpstreamConnection extends BaseEntity {

    /** 所属供应商 ID，必填且必须属于同一租户 */
    private Long providerId;

    /** 供应商内连接编码，必填且在同一供应商内唯一 */
    private String code;

    /** 连接展示名称，必填 */
    private String displayName;

    /** 上游协议类型，必填 */
    private AiProtocolType protocolType;

    /** 上游 Base URL，必填，只允许非敏感绝对 http/https 地址 */
    private String baseUrl;

    /** 目录状态，必填 */
    private AiCatalogStatus status;

    /** 连接描述，可选 */
    private String description;
}
