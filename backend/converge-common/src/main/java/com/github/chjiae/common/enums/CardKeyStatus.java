package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 卡密状态枚举，标识卡密的生命周期状态
 */
@Getter
@AllArgsConstructor
public enum CardKeyStatus {

    /** 未使用 */
    UNUSED("未使用"),
    /** 已兑换 */
    REDEEMED("已兑换"),
    /** 已过期 */
    EXPIRED("已过期");

    /** 状态描述 */
    private final String description;
}
