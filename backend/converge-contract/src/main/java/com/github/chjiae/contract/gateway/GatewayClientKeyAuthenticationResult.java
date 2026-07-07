package com.github.chjiae.contract.gateway;

/**
 * Client API Key 认证结果。
 *
 * @param authenticated 是否认证成功
 * @param principal 认证成功后的主体
 * @param errorCode 安全错误码
 */
public record GatewayClientKeyAuthenticationResult(
        boolean authenticated,
        GatewayClientPrincipal principal,
        String errorCode
) {

    /**
     * 构造认证成功结果。
     *
     * @param principal 调用方主体
     * @return 成功结果
     */
    public static GatewayClientKeyAuthenticationResult success(GatewayClientPrincipal principal) {
        return new GatewayClientKeyAuthenticationResult(true, principal, null);
    }

    /**
     * 构造认证失败结果。
     *
     * @param errorCode 安全错误码
     * @return 失败结果
     */
    public static GatewayClientKeyAuthenticationResult failure(String errorCode) {
        return new GatewayClientKeyAuthenticationResult(false, null, errorCode);
    }
}
