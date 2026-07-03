package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 租户申请类型枚举，用于标识租户注册申请的方式
 */
@Getter
@AllArgsConstructor
public enum ApplicationType {

    /** 注册充值，审核通过后需充值激活 */
    REGISTER("注册充值"),
    /** 申请试用，审核通过后获得 7 天试用期 */
    TRIAL("申请试用");

    /** 类型描述 */
    private final String description;
}
