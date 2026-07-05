package com.github.chjiae.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.SubscriptionPlanType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订阅套餐配置实体，对应 subscription_plan 表。
 * 用于配置套餐价格、有效期、权益说明和当前折扣价。
 */
@Data
@TableName("subscription_plan")
public class SubscriptionPlan {

    /** 主键 ID */
    private Long id;

    /** 套餐编码，系统内唯一 */
    private String code;

    /** 套餐展示名称 */
    private String name;

    /** 套餐类型 */
    private SubscriptionPlanType planType;

    /** 套餐有效月数 */
    private Integer durationMonths;

    /** 套餐原价 */
    private BigDecimal originalPrice;

    /** 当前折扣活动名称 */
    private String discountName;

    /** 当前折扣成交价 */
    private BigDecimal discountPrice;

    /** 折扣开始时间 */
    private LocalDateTime discountStartAt;

    /** 折扣结束时间 */
    private LocalDateTime discountEndAt;

    /** 权益说明，每行一条 */
    private String benefits;

    /** 是否启用 */
    private Boolean enabled;

    /** 是否推荐展示 */
    private Boolean recommended;

    /** 展示排序，越小越靠前 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
