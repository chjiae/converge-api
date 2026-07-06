package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiProviderKind;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 供应商实体，对应 ai_provider 表。
 * 表示租户内登记的逻辑上游供应商，不包含任何凭据或运行时路由信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_provider")
public class AiProvider extends BaseEntity {

    /** 租户内供应商编码，必填且在租户内唯一 */
    private String code;

    /** 供应商展示名称，必填 */
    private String displayName;

    /** 供应商类型，必填 */
    private AiProviderKind providerKind;

    /** 目录状态，必填 */
    private AiCatalogStatus status;

    /** 供应商描述，可选 */
    private String description;
}
