package com.github.chjiae.service.dto.auth;

import lombok.Builder;
import lombok.Data;

/**
 * 注册邮箱验证码校验响应参数。
 */
@Data
@Builder
public class VerifyRegisterEmailCodeResponse {

    /** 注册凭据，后续创建账户时必须携带 */
    private String verificationToken;
}
