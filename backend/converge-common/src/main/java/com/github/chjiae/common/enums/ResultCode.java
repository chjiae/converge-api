package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Common response status codes.
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden"),
    NOT_FOUND(404, "not found"),
    INTERNAL_ERROR(500, "internal server error"),

    /** 权限不足 */
    PERMISSION_DENIED(40300, "权限不足"),
    /** 租户已停用 */
    TENANT_DISABLED(40301, "租户已停用"),
    /** 租户已过期 */
    TENANT_EXPIRED(40302, "租户已过期"),
    /** 租户待激活 */
    TENANT_PENDING(40303, "租户待激活"),
    /** 租户不存在 */
    TENANT_NOT_FOUND(40401, "租户不存在"),
    /** 申请记录不存在 */
    APPLICATION_NOT_FOUND(40402, "申请记录不存在"),
    /** 订阅记录不存在 */
    SUBSCRIPTION_NOT_FOUND(40403, "订阅记录不存在");

    private final int code;
    private final String message;
}
