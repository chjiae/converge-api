package com.github.chjiae.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用邮件配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.mail")
public class AppMailProperties {
    /** 发件人显示名称 */
    private String fromName = "Converge API";
}
