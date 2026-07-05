package com.github.chjiae.service.dto.subscription;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订阅套餐响应 DTO，返回给前端展示套餐卡片和后台配置列表。
 */
@Data
@Builder
public class SubscriptionPlanResponse {

    /** 套餐 ID */
    private Long id;

    /** 套餐编码 */
    private String code;

    /** 套餐名称 */
    private String name;

    /** 套餐类型 */
    private String planType;

    /** 有效月数 */
    private Integer durationMonths;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 最终成交价，折扣生效时为折扣价，否则为原价 */
    private BigDecimal finalPrice;

    /** 当前折扣活动名称 */
    private String discountName;

    /** 当前折扣价 */
    private BigDecimal discountPrice;

    /** 折扣开始时间 */
    private LocalDateTime discountStartAt;

    /** 折扣结束时间 */
    private LocalDateTime discountEndAt;

    /** 折扣当前是否生效 */
    private Boolean hasActiveDiscount;

    /** 权益说明列表 */
    private List<String> benefits;

    /** 是否启用 */
    private Boolean enabled;

    /** 是否推荐展示 */
    private Boolean recommended;

    /** 展示排序 */
    private Integer sortOrder;
}
