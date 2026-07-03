package com.github.chjiae.service.exception;

import com.github.chjiae.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spring Security 异常处理器。
 * 由于 GlobalExceptionHandler 在 converge-common 模块中无法引用 Spring Security 类，
 * 因此在本模块单独处理认证和授权相关异常。
 */
@Slf4j
@RestControllerAdvice
public class SecurityExceptionHandler {

    /**
     * 处理认证异常（未认证或认证失败）
     *
     * @param e 认证异常
     * @return 401 响应
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return Result.fail(401, "认证失败，请重新登录");
    }

    /**
     * 处理授权异常（权限不足）
     *
     * @param e 访问拒绝异常
     * @return 403 响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.fail(403, "权限不足，无法访问该资源");
    }
}
