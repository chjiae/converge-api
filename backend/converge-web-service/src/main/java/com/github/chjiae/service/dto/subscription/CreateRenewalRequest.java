package com.github.chjiae.service.dto.subscription;

import com.github.chjiae.common.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 租户续费下单请求参数。
 * 前端只提交套餐和支付方式，金额与周期由后端根据套餐配置计算。
 */
@Data
public class CreateRenewalRequest {

    /** 套餐 ID（必填） */
    @NotNull(message = "套餐不能为空")
    private Long planId;

    /** 支付方式（必填） */
    @NotNull(message = "支付方式不能为空")
    private PaymentMethod paymentMethod;

    /** 备注（可选，仅线下支付时使用） */
    private String remark;
}
