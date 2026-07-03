package com.github.chjiae.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.PaymentMethod;
import com.github.chjiae.common.enums.SubscriptionPlanType;
import com.github.chjiae.common.enums.SubscriptionStatus;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 订阅/订单实体，对应 subscription 表。
 * 记录租户的订阅订单信息，包含套餐类型、金额、有效期和支付状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("subscription")
public class Subscription extends BaseEntity {

    /** 套餐类型：TRIAL / MONTHLY / QUARTERLY / YEARLY / CUSTOM */
    private SubscriptionPlanType planType;

    /** 金额（试用为 0） */
    private BigDecimal amount;

    /** 生效日期 */
    private LocalDate startDate;

    /** 到期日期 */
    private LocalDate endDate;

    /** 订阅状态：PENDING / PAID / ACTIVE / EXPIRED / CANCELLED */
    private SubscriptionStatus status;

    /** 支付方式：ALIPAY / WECHAT / CARD_KEY / OFFLINE */
    private PaymentMethod paymentMethod;

    /** 支付凭证号 */
    private String paymentRef;

    /** 备注 */
    private String remark;

    /** 创建人 ID（租户管理员或超管） */
    private Long createdBy;
}
