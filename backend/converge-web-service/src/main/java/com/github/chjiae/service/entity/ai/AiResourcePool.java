package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiSelectionPolicy;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 上游资源池实体，对应 ai_resource_pool 表。
 * 资源池是上游调度侧分组，不保存凭据、价格、倍率或运行时限流状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_resource_pool")
public class AiResourcePool extends BaseEntity {

    /** 资源池编码，必填且在租户内唯一 */
    private String code;

    /** 资源池展示名称，必填 */
    private String displayName;

    /** 资源池描述，可选 */
    private String description;

    /** 管理状态：ENABLED / DISABLED */
    private AiCatalogStatus adminStatus;

    /** 选择策略，本阶段固定为 PRIORITY_WEIGHTED */
    private AiSelectionPolicy selectionPolicy;
}
