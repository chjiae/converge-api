package com.github.chjiae.service.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * AI Gateway 管理代理配置。
 * 该配置仅用于控制面调用 Gateway 内部安全状态与测试接口，不会返回给前端。
 */
@Slf4j
@Getter
@Configuration
public class GatewayAdminProperties {

    /** 是否启用 Gateway 管理代理。 */
    @Value("${ai.gateway.admin.enabled:false}")
    private boolean enabled;

    /** Gateway 管理代理基础地址，仅服务端内部使用。 */
    @Value("${ai.gateway.admin.base-url:}")
    private String baseUrl;

    /** 连接超时时间，单位毫秒。 */
    @Value("${ai.gateway.admin.connect-timeout-ms:1000}")
    private long connectTimeoutMs;

    /** 读取超时时间，单位毫秒。 */
    @Value("${ai.gateway.admin.read-timeout-ms:3000}")
    private long readTimeoutMs;

    /**
     * 启动时校验配置范围。
     */
    @PostConstruct
    public void validate() {
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalStateException("AI Gateway 管理代理超时配置必须大于 0");
        }
        if (!enabled) {
            log.info("AI Gateway 管理代理未启用");
            return;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("AI Gateway 管理代理已启用但未配置基础地址");
        }
        validateBaseUrl(baseUrl);
        log.info("AI Gateway 管理代理配置已启用");
    }

    /**
     * 校验基础地址，避免带入凭据或客户端可控路径。
     *
     * @param value 基础地址
     */
    private void validateBaseUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalStateException("AI Gateway 管理代理地址仅允许 http 或 https");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalStateException("AI Gateway 管理代理地址必须包含 host");
            }
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException("AI Gateway 管理代理地址不能包含凭据、query 或 fragment");
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("AI Gateway 管理代理地址格式不正确", e);
        }
    }
}
