package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订阅状态枚举，用于标识订阅/订单在生命周期中的当前状态
 */
@Getter
@AllArgsConstructor
public enum SubscriptionStatus {

    /** 待支付，订单已创建但尚未完成支付 */
    PENDING("待支付"),
    /** 已支付，支付已完成但尚未生效 */
    PAID("已支付"),
    /** 生效中，订阅正在有效期内 */
    ACTIVE("生效中"),
    /** 已过期，订阅到期未续费 */
    EXPIRED("已过期"),
    /** 已取消，订阅被手动取消 */
    CANCELLED("已取消");

    /** 状态描述 */
    private final String description;
}
