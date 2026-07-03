package com.github.chjiae.service.dto.auth;

import lombok.Builder;
import lombok.Data;

/**
 * 令牌响应，包含访问令牌、刷新令牌和用户信息
 */
@Data
@Builder
public class TokenResponse {

    /** 访问令牌 */
    private String accessToken;

    /** 刷新令牌 */
    private String refreshToken;

    /** 用户信息 */
    private UserInfoResponse userInfo;
}
