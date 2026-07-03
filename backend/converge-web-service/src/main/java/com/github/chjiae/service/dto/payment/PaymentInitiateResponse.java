package com.github.chjiae.service.dto.payment;

import lombok.Builder;
import lombok.Data;

/**
 * 发起支付响应
 */
@Data
@Builder
public class PaymentInitiateResponse {

    /** 支付跳转链接或表单 HTML */
    private String paymentUrl;

    /** 商户订单号 */
    private String outTradeNo;
}
