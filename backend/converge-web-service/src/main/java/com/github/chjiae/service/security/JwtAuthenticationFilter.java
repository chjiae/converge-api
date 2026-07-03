package com.github.chjiae.service.security;

import com.github.chjiae.service.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器，优先从 HttpOnly Cookie 解析 JWT，向后兼容 Authorization Bearer 头。
 * 解析成功后设置 SecurityContext，同时将租户 ID 写入 TenantContext 以实现数据隔离。
 * 如果 token 无效或缺失，直接放行（由后续的 Security 配置决定是否拒绝）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT 令牌提供者 */
    private final JwtTokenProvider jwtTokenProvider;

    /** Token 黑名单服务 */
    private final TokenBlacklistService tokenBlacklistService;

    /** 自定义用户详情服务 */
    private final CustomUserDetailsService userDetailsService;

    /**
     * 执行 JWT 认证过滤逻辑
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. 优先从 Cookie 提取 access_token，向后兼容 Authorization Bearer 头
            String token = extractTokenFromRequest(request);

            // 2. 验证 token 有效性
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                // 3. 检查 Token 是否在黑名单中
                String jti = jwtTokenProvider.getJtiFromToken(token);
                if (jti != null && tokenBlacklistService.isBlacklisted(jti)) {
                    log.warn("JWT 认证拒绝，Token 已在黑名单中，jti: {}", jti);
                    filterChain.doFilter(request, response);
                    return;
                }

                // 4. 从 token 解析用户名
                String username = jwtTokenProvider.getUsernameFromToken(token);

                // 5. 加载用户详情（包含角色权限）
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 6. 创建认证令牌并设置到 SecurityContextHolder
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 7. 将 tenantId 写入 TenantContext，实现租户数据隔离
                Long tenantId = jwtTokenProvider.getTenantIdFromToken(token);
                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                }

                log.debug("JWT 认证成功，用户: {}，租户: {}", username, tenantId);
            }
        } catch (Exception ex) {
            log.error("JWT 认证过程异常: {}", ex.getMessage());
        }

        // 8. 继续过滤器链（无论认证成功与否，由 Security 配置决定是否拦截）
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中提取 JWT Token
     * 优先从 HttpOnly Cookie 读取，向后兼容 Authorization Bearer 头
     *
     * @param request HTTP 请求
     * @return Token 字符串，如果不存在则返回 null
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        // 优先从 Cookie 读取（HttpOnly，更安全）
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        // 向后兼容：从 Authorization Bearer 头读取（集成测试使用）
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
