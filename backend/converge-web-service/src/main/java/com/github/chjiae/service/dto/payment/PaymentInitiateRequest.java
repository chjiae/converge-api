package com.github.chjiae.service.dto.payment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起在线支付请求参数
 */
@Data
public class PaymentInitiateRequest {

    /** 订阅 ID */
    @NotNull(message = "订阅 ID 不能为空")
    private Long subscriptionId;

    /** 支付方式：ALIPAY 或 WECHAT */
    @NotNull(message = "支付方式不能为空")
    private String paymentMethod;

    /** 支付完成后前端回跳地址 */
    private String returnUrl;
}
