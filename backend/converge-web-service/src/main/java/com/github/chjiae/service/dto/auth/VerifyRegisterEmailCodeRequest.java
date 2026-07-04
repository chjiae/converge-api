package com.github.chjiae.service.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 校验注册邮箱验证码请求参数。
 */
@Data
public class VerifyRegisterEmailCodeRequest {

    /** 邮箱地址（必填，需与发送验证码时一致） */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 邮箱验证码（必填，6 位数字） */
    @NotBlank(message = "邮箱验证码不能为空")
    private String code;
}
