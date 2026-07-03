package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订阅套餐类型枚举，用于标识订阅的计费周期
 */
@Getter
@AllArgsConstructor
public enum SubscriptionPlanType {

    /** 试用套餐，通常为 7 天免费试用 */
    TRIAL("试用"),
    /** 月付套餐 */
    MONTHLY("月付"),
    /** 季付套餐 */
    QUARTERLY("季付"),
    /** 年付套餐 */
    YEARLY("年付"),
    /** 自定义套餐，由管理员手动设定 */
    CUSTOM("自定义");

    /** 类型描述 */
    private final String description;
}
