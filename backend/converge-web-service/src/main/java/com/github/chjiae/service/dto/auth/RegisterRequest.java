package com.github.chjiae.service.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注册请求参数
 */
@Data
public class RegisterRequest {

    /** 用户名（必填） */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 邮箱（必填，需符合邮箱格式） */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 密码（必填） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 租户编码（必填，要加入的租户编码） */
    @NotBlank(message = "租户编码不能为空")
    private String tenantCode;

    /** 邮箱验证凭据（必填，验证邮箱成功后由后端签发） */
    @NotBlank(message = "请先完成邮箱验证")
    private String verificationToken;
}
