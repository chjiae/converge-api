package com.github.chjiae.service.payment;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 支付订单请求参数，封装创建支付订单所需的信息。
 */
@Data
@Builder
public class PaymentOrder {
    /** 商户订单号（由系统生成，全局唯一） */
    private String outTradeNo;
    /** 订单标题/描述 */
    private String subject;
    /** 订单金额（元） */
    private BigDecimal amount;
    /** 支付完成后前端回跳地址 */
    private String returnUrl;
}
