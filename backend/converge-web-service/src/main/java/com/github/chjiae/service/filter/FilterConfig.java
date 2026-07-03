package com.github.chjiae.service.filter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 过滤器配置类，注册自定义过滤器。
 */
@Configuration
public class FilterConfig {

    /**
     * 注册租户上下文清理过滤器
     * @return 过滤器注册配置
     */
    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilter() {
        FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TenantContextFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(1); // 高优先级
        registration.setName("tenantContextFilter");
        return registration;
    }
}
