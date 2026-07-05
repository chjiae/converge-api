package com.github.chjiae.service.dto.subscription;

import com.github.chjiae.common.enums.SubscriptionPlanType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 更新订阅套餐请求参数。
 */
@Data
public class UpdateSubscriptionPlanRequest {

    /** 套餐名称（必填） */
    @NotBlank(message = "套餐名称不能为空")
    private String name;

    /** 套餐类型（必填） */
    @NotNull(message = "套餐类型不能为空")
    private SubscriptionPlanType planType;

    /** 有效月数（必填，至少 1 个月） */
    @NotNull(message = "有效月数不能为空")
    @Min(value = 1, message = "有效月数至少为 1")
    private Integer durationMonths;

    /** 原价（必填，必须大于 0） */
    @NotNull(message = "原价不能为空")
    @DecimalMin(value = "0.01", message = "原价必须大于 0")
    private BigDecimal originalPrice;

    /** 当前折扣活动名称（可选） */
    private String discountName;

    /** 当前折扣成交价（可选） */
    @DecimalMin(value = "0.01", message = "折扣价必须大于 0")
    private BigDecimal discountPrice;

    /** 折扣开始时间（可选） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime discountStartAt;

    /** 折扣结束时间（可选） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime discountEndAt;

    /** 权益说明，每行一条（可选） */
    private String benefits;

    /** 是否启用（必填） */
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    /** 是否推荐展示（必填） */
    @NotNull(message = "推荐状态不能为空")
    private Boolean recommended;

    /** 展示排序（必填） */
    @NotNull(message = "排序不能为空")
    private Integer sortOrder;
}
