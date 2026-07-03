package com.github.chjiae.service.filter;

import com.github.chjiae.service.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户上下文清理过滤器。
 * 在每个请求结束后清理 TenantContext，防止线程复用时数据污染。
 */
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后清理租户上下文，防止内存泄漏
            TenantContext.clear();
        }
    }
}
