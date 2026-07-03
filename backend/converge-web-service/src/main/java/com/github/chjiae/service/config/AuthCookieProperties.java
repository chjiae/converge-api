package com.github.chjiae.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证 Cookie 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth.cookie")
public class AuthCookieProperties {
    /** 是否仅 HTTPS 传输（生产环境应为 true） */
    private boolean secure = false;
    /** SameSite 属性（Strict / Lax / None） */
    private String sameSite = "Strict";
    /** Cookie 域名（空表示当前域名） */
    private String domain = "";
}
