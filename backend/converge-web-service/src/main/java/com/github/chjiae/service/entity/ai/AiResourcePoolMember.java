package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 资源池成员实体，对应 ai_resource_pool_member 表。
 * 记录资源池与执行资源的 tenant 级关联，以及池内优先级和权重。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_resource_pool_member")
public class AiResourcePoolMember extends BaseEntity {

    /** 资源池 ID，必填且必须属于同一租户 */
    private Long resourcePoolId;

    /** 执行资源 ID，必填且必须属于同一租户 */
    private Long executionResourceId;

    /** 管理状态：ENABLED / DISABLED */
    private AiCatalogStatus adminStatus;

    /** 池内成员优先级，数值越大越优先 */
    private Integer priority;

    /** 同优先级内正整数权重 */
    private Integer weight;
}
