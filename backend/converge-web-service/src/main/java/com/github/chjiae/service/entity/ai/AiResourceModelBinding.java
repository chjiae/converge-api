package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiCanonicalOperation;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 资源模型绑定实体，对应 ai_resource_model_binding 表。
 * 表达一个执行资源对一个公开模型和一个规范化操作的精确上游模型映射。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_resource_model_binding")
public class AiResourceModelBinding extends BaseEntity {

    /** 执行资源 ID，必填且必须属于同一租户 */
    private Long executionResourceId;

    /** 公开模型 ID，必填且必须属于同一租户 */
    private Long publicModelId;

    /** 规范化操作类型，必填 */
    private AiCanonicalOperation canonicalOperation;

    /** 精确上游模型名称，必填，禁止 wildcard/regex */
    private String upstreamModelName;

    /** 管理状态：ENABLED / DISABLED */
    private AiCatalogStatus adminStatus;
}
