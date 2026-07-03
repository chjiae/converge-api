package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付方式枚举，用于标识订阅订单的支付渠道
 */
@Getter
@AllArgsConstructor
public enum PaymentMethod {

    /** 支付宝在线支付 */
    ALIPAY("支付宝"),
    /** 微信支付 */
    WECHAT("微信支付"),
    /** 卡密支付（后期支持） */
    CARD_KEY("卡密"),
    /** 线下支付，需联系管理员 */
    OFFLINE("线下支付");

    /** 支付方式描述 */
    private final String description;
}
