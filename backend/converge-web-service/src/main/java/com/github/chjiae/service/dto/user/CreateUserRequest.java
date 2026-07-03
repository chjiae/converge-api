package com.github.chjiae.service.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建用户请求参数。
 * 租户管理员在租户内创建新用户时使用。
 */
@Data
public class CreateUserRequest {

    /** 用户名（必填） */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 邮箱（必填） */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 密码（必填） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 手机号（可选） */
    private String phone;
}
