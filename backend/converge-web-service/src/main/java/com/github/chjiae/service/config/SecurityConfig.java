package com.github.chjiae.service.config;

import com.github.chjiae.service.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置类。
 * 配置无状态会话、JWT 过滤器、公开端点和权限规则。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * 密码编码器，使用 BCrypt 算法
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器，用于手动认证（如登录接口）
     *
     * @param config 认证配置
     * @return AuthenticationManager 实例
     * @throws Exception 配置异常
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 安全过滤器链配置
     * 禁用 CSRF（无状态 API）、配置公开端点、注册 JWT 过滤器
     *
     * @param http     HttpSecurity 配置对象
     * @param jwtFilter JWT 认证过滤器
     * @return 构建完成的 SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 认证相关公开端点
                .requestMatchers("/api/v1/auth/**").permitAll()
                // 租户申请提交（公开）
                .requestMatchers(HttpMethod.POST, "/api/v1/applications").permitAll()
                // 租户申请查询（公开）
                .requestMatchers(HttpMethod.GET, "/api/v1/applications/**").permitAll()
                // 健康检查
                .requestMatchers("/api/health").permitAll()
                // Spring 默认错误端点
                .requestMatchers("/error").permitAll()
                // 其他端点需要认证
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
