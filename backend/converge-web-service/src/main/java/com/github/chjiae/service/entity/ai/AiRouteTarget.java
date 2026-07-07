package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 静态路由目标实体，对应 ai_route_target 表。
 * 记录 RoutePolicy 到 ResourcePool 的关联，以及目标池优先级和权重。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_route_target")
public class AiRouteTarget extends BaseEntity {

    /** 路由策略 ID，必填且必须属于同一租户 */
    private Long routePolicyId;

    /** 资源池 ID，必填且必须属于同一租户 */
    private Long resourcePoolId;

    /** 管理状态：ENABLED / DISABLED */
    private AiCatalogStatus adminStatus;

    /** 目标优先级，数值越大越优先 */
    private Integer priority;

    /** 同优先级目标内正整数权重 */
    private Integer weight;
}
