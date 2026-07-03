package com.github.chjiae.service.payment;

import lombok.Builder;
import lombok.Data;

/**
 * 支付网关操作结果，封装创建订单、回调处理等操作的返回信息。
 */
@Data
@Builder
public class PaymentResult {
    /** 操作是否成功 */
    private boolean success;
    /** 支付跳转链接或表单 HTML（创建订单时返回） */
    private String paymentUrl;
    /** 商户订单号 */
    private String outTradeNo;
    /** 操作结果消息（错误时包含原因） */
    private String message;
    /** 支付是否已完成（回调处理时，true 表示用户已完成支付） */
    private boolean paid;
}
