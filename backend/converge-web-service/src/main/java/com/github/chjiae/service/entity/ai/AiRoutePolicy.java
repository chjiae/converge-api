package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiCanonicalOperation;
import com.github.chjiae.common.enums.AiRoutePolicyStatus;
import com.github.chjiae.common.enums.AiSelectionPolicy;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 静态路由策略实体，对应 ai_route_policy 表。
 * 一条策略表示当前租户下一个公开模型和一个规范化操作的默认上游静态路由。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_route_policy")
public class AiRoutePolicy extends BaseEntity {

    /** 公开模型 ID，必填且必须属于同一租户 */
    private Long publicModelId;

    /** 规范化操作类型，必填 */
    private AiCanonicalOperation canonicalOperation;

    /** 策略展示名称，必填 */
    private String displayName;

    /** 策略描述，可选 */
    private String description;

    /** 管理状态：DRAFT / ENABLED / DISABLED */
    private AiRoutePolicyStatus adminStatus;

    /** 选择策略，本阶段固定为 PRIORITY_WEIGHTED */
    private AiSelectionPolicy selectionPolicy;
}
