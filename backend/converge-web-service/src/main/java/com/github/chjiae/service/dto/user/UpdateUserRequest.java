package com.github.chjiae.service.dto.user;

import lombok.Data;

/**
 * 更新用户请求参数。
 * 用户更新个人信息时使用，仅允许修改手机号和邮箱。
 */
@Data
public class UpdateUserRequest {

    /** 手机号（可选） */
    private String phone;

    /** 邮箱（可选） */
    private String email;
}
