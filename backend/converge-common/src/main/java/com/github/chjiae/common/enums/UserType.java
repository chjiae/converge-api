package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户类型枚举，用于区分平台级用户和租户级用户
 */
@Getter
@AllArgsConstructor
public enum UserType {

    /** 超级管理员，不隶属于任何租户，拥有平台级全部管理权限 */
    SUPER_ADMIN("超级管理员"),
    /** 平台运营人员，负责审核申请、管理订阅等 */
    PLATFORM_OPERATOR("平台运营"),
    /** 租户用户，隶属于某个租户 */
    TENANT_USER("租户用户");

    /** 类型描述 */
    private final String description;
}
