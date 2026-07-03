package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 租户状态枚举，用于标识租户在生命周期中的当前状态
 */
@Getter
@AllArgsConstructor
public enum TenantStatus {

    /** 待激活 */
    PENDING("待激活"),
    /** 试用中 */
    TRIAL("试用中"),
    /** 正常 */
    ACTIVE("正常"),
    /** 已停用 */
    DISABLED("已停用"),
    /** 已过期 */
    EXPIRED("已过期"),
    /** 已删除 */
    DELETED("已删除");

    /** 状态描述 */
    private final String description;
}
