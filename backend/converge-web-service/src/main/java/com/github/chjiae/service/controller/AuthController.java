package com.github.chjiae.service.controller;

import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.dto.auth.LoginRequest;
import com.github.chjiae.service.dto.auth.RegisterRequest;
import com.github.chjiae.service.dto.auth.TokenResponse;
import com.github.chjiae.service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器，提供登录、注册、Token 刷新等公开接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 认证服务 */
    private final AuthService authService;

    /**
     * 用户登录
     *
     * @param request 登录请求参数
     * @return 令牌响应
     */
    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("登录接口调用，用户名: {}", request.getUsername());
        TokenResponse response = authService.login(request);
        return Result.ok(response);
    }

    /**
     * 用户注册
     *
     * @param request 注册请求参数
     * @return 令牌响应
     */
    @PostMapping("/register")
    public Result<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("注册接口调用，用户名: {}，租户编码: {}", request.getUsername(), request.getTenantCode());
        TokenResponse response = authService.register(request);
        return Result.ok(response);
    }

    /**
     * 刷新访问令牌
     *
     * @param body 包含 refreshToken 的请求体
     * @return 新的令牌响应
     */
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        log.info("令牌刷新接口调用");
        TokenResponse response = authService.refreshToken(refreshToken);
        return Result.ok(response);
    }

    /**
     * 用户登出
     * 当前无状态 JWT，登出仅需返回成功。
     * 后续可配合 Redis 黑名单实现真正的登出。
     *
     * @return 成功响应
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        log.info("登出接口调用");
        return Result.ok();
    }
}
