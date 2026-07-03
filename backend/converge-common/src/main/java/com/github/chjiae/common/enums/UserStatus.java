package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户状态枚举，用于标识用户账号的启用/停用状态
 */
@Getter
@AllArgsConstructor
public enum UserStatus {

    /** 正常状态，可正常使用系统 */
    ACTIVE("正常"),
    /** 已停用，无法登录和使用系统 */
    DISABLED("已停用");

    /** 状态描述 */
    private final String description;
}
