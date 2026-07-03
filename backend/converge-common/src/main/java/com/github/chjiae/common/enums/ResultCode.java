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
    INTERNAL_ERROR(500, "internal server error");

    private final int code;
    private final String message;
}
