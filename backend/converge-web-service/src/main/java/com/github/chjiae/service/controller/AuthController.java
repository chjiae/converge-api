package com.github.chjiae.service.controller;

import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.annotation.Auditable;
import com.github.chjiae.service.config.AuthCookieProperties;
import com.github.chjiae.service.dto.auth.LoginRequest;
import com.github.chjiae.service.dto.auth.RegisterRequest;
import com.github.chjiae.service.dto.auth.TokenResponse;
import com.github.chjiae.service.security.JwtTokenProvider;
import com.github.chjiae.service.security.TokenBlacklistService;
import com.github.chjiae.service.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器，提供登录、注册、Token 刷新等公开接口。
 * 支持 HttpOnly Cookie 和 Bearer Token 双模式认证，Cookie 优先。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 认证服务 */
    private final AuthService authService;

    /** JWT 令牌提供者 */
    private final JwtTokenProvider jwtTokenProvider;

    /** Token 黑名单服务 */
    private final TokenBlacklistService tokenBlacklistService;

    /** 认证 Cookie 配置属性 */
    private final AuthCookieProperties cookieProperties;

    /**
     * 用户登录
     *
     * @param request      登录请求参数
     * @param httpResponse HTTP 响应（用于设置认证 Cookie）
     * @return 令牌响应
     */
    @Auditable(module = "auth", action = "login")
    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletResponse httpResponse) {
        log.info("登录接口调用，用户名: {}", request.getUsername());
        TokenResponse response = authService.login(request);

        // 设置 HttpOnly Cookie（前端无法通过 JavaScript 读取，防御 XSS）
        setAuthCookies(httpResponse, response);

        return Result.ok(response);
    }

    /**
     * 用户注册
     *
     * @param request      注册请求参数
     * @param httpResponse HTTP 响应（用于设置认证 Cookie）
     * @return 令牌响应
     */
    @PostMapping("/register")
    public Result<TokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                          HttpServletResponse httpResponse) {
        log.info("注册接口调用，用户名: {}，租户编码: {}", request.getUsername(), request.getTenantCode());
        TokenResponse response = authService.register(request);

        // 设置 HttpOnly Cookie（前端无法通过 JavaScript 读取，防御 XSS）
        setAuthCookies(httpResponse, response);

        return Result.ok(response);
    }

    /**
     * 刷新访问令牌
     * 支持从请求体或 Cookie 读取 refreshToken。
     *
     * @param body         包含 refreshToken 的请求体
     * @param request      HTTP 请求（用于从 Cookie 读取 refreshToken）
     * @param httpResponse HTTP 响应（用于设置新的认证 Cookie）
     * @return 新的令牌响应
     */
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@RequestBody Map<String, String> body,
                                         HttpServletRequest request,
                                         HttpServletResponse httpResponse) {
        String refreshToken = body.get("refreshToken");
        // 向后兼容：如果请求体中没有 refreshToken，尝试从 Cookie 读取
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = extractCookieValue(request, "refresh_token");
        }
        log.info("令牌刷新接口调用");
        TokenResponse response = authService.refreshToken(refreshToken);

        // 设置新的 HttpOnly Cookie
        setAuthCookies(httpResponse, response);

        return Result.ok(response);
    }

    /**
     * 用户登出
     * 将当前 Access Token 加入 Redis 黑名单，使其在剩余有效期内无法继续使用。
     * 同时清除认证 Cookie。
     *
     * @param request      HTTP 请求（用于提取 Authorization 头中的 Token）
     * @param httpResponse HTTP 响应（用于清除认证 Cookie）
     * @return 成功响应
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse httpResponse) {
        log.info("登出接口调用");

        // 从请求头提取 Token
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            if (jwtTokenProvider.validateToken(token)) {
                String jti = jwtTokenProvider.getJtiFromToken(token);
                long remaining = jwtTokenProvider.getRemainingExpiration(token);
                if (jti != null && remaining > 0) {
                    tokenBlacklistService.blacklist(jti, remaining);
                    log.info("Access Token 已加入黑名单，jti: {}，剩余有效期: {} 毫秒", jti, remaining);
                }
            }
        }

        // 清除认证 Cookie
        ResponseCookie clearAccess = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path("/")
                .maxAge(0)
                .build();
        ResponseCookie clearRefresh = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path("/api/v1/auth/refresh")
                .maxAge(0)
                .build();
        httpResponse.addHeader("Set-Cookie", clearAccess.toString());
        httpResponse.addHeader("Set-Cookie", clearRefresh.toString());

        return Result.ok();
    }

    /**
     * 设置认证相关的 HttpOnly Cookie
     * 同时设置 access_token 和 refresh_token，前端无法通过 JavaScript 读取（防御 XSS）。
     *
     * @param httpResponse HTTP 响应
     * @param response     令牌响应数据
     */
    private void setAuthCookies(HttpServletResponse httpResponse, TokenResponse response) {
        // access_token Cookie：全站发送，有效期 2 小时
        ResponseCookie accessCookie = ResponseCookie.from("access_token", response.getAccessToken())
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path("/")
                .maxAge(2 * 60 * 60)
                .build();
        // refresh_token Cookie：仅刷新端点发送，减少暴露面，有效期 7 天
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", response.getRefreshToken())
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path("/api/v1/auth/refresh")
                .maxAge(7 * 24 * 60 * 60)
                .build();
        httpResponse.addHeader("Set-Cookie", accessCookie.toString());
        httpResponse.addHeader("Set-Cookie", refreshCookie.toString());
    }

    /**
     * 从请求 Cookie 中提取指定名称的值
     *
     * @param request    HTTP 请求
     * @param cookieName Cookie 名称
     * @return Cookie 值，如果不存在则返回 null
     */
    private String extractCookieValue(HttpServletRequest request, String cookieName) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
