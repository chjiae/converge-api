package com.github.chjiae.service.dto.subscription;

import com.github.chjiae.common.enums.PaymentMethod;
import com.github.chjiae.common.enums.SubscriptionPlanType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建订阅请求参数。
 * 超管/运营人员为指定租户创建线下订阅记录时使用。
 */
@Data
public class CreateSubscriptionRequest {

    /** 租户 ID（必填） */
    @NotNull(message = "租户 ID 不能为空")
    private Long tenantId;

    /** 套餐类型（必填） */
    @NotNull(message = "套餐类型不能为空")
    private SubscriptionPlanType planType;

    /** 金额（必填） */
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    /** 生效日期（必填） */
    @NotNull(message = "生效日期不能为空")
    private LocalDate startDate;

    /** 到期日期（必填） */
    @NotNull(message = "到期日期不能为空")
    private LocalDate endDate;

    /** 支付方式（必填） */
    @NotNull(message = "支付方式不能为空")
    private PaymentMethod paymentMethod;

    /** 备注（可选） */
    private String remark;
}
