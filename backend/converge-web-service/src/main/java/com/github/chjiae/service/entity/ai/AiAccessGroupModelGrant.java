package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiCanonicalOperation;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 访问组模型授权实体，对应 ai_access_group_model_grant 表。
 * 授权粒度固定为 PublicModel + CanonicalOperation 的精确组合。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_access_group_model_grant")
public class AiAccessGroupModelGrant extends BaseEntity {

    /** 访问组 ID，必填且必须属于同一租户 */
    private Long accessGroupId;

    /** 公开模型 ID，必填且必须属于同一租户 */
    private Long publicModelId;

    /** 规范化操作类型，必填 */
    private AiCanonicalOperation canonicalOperation;

    /** 管理状态：ENABLED / DISABLED */
    private AiCatalogStatus adminStatus;
}
