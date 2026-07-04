package com.github.chjiae.service.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送注册邮箱验证码请求参数。
 */
@Data
public class SendRegisterEmailCodeRequest {

    /** 邮箱地址（必填，需符合邮箱格式） */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 人机验证码 ID（必填） */
    @NotBlank(message = "人机验证码不能为空")
    private String captchaId;

    /** 用户输入的人机验证码答案（必填） */
    @NotBlank(message = "人机验证码答案不能为空")
    private String captchaAnswer;
}
