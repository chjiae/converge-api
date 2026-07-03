package com.github.chjiae.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.UserStatus;
import com.github.chjiae.common.enums.UserType;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体，对应 user 表。
 * 表示系统中的一个用户账号，包含登录凭证、用户类型和状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"user\"")
public class User extends BaseEntity {

    /** 用户名（租户内唯一，超管全局唯一） */
    private String username;

    /** 邮箱（租户内唯一，超管全局唯一） */
    private String email;

    /** 密码哈希 */
    private String passwordHash;

    /** 手机号 */
    private String phone;

    /** 用户状态：ACTIVE / DISABLED */
    private UserStatus status;

    /** 用户类型：SUPER_ADMIN / PLATFORM_OPERATOR / TENANT_USER */
    private UserType userType;
}
