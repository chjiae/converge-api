package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 公开模型实体，对应 ai_public_model 表。
 * 表示租户准备向下游暴露的稳定模型别名，不直接绑定上游真实模型名称。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_public_model")
public class AiPublicModel extends BaseEntity {

    /** 下游公开模型别名，必填且在租户内唯一 */
    private String code;

    /** 公开模型展示名称，必填 */
    private String displayName;

    /** 模型族或产品线，必填 */
    private String modelFamily;

    /** 目录状态，必填 */
    private AiCatalogStatus status;

    /** 公开模型描述，可选 */
    private String description;
}
