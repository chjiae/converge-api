package com.github.chjiae.service.service.ai;

import com.github.chjiae.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * AI 上游 Base URL 规范化组件。
 * 仅做本阶段要求的静态格式校验，不进行 DNS、网络探测或 SSRF 检测。
 */
@Component
public class AiBaseUrlNormalizer {

    /**
     * 校验并规范化 Base URL。
     *
     * @param rawBaseUrl 原始 Base URL
     * @return 规范化后的 Base URL，末尾统一带斜杠
     * @throws BusinessException URL 不符合本阶段安全规则时抛出
     */
    public String normalize(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new BusinessException(400, "Base URL 不能为空");
        }

        URI uri = parse(rawBaseUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new BusinessException(400, "Base URL 仅允许 http 或 https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException(400, "Base URL 必须包含 host");
        }
        if (uri.getRawUserInfo() != null) {
            throw new BusinessException(400, "Base URL 禁止包含用户名或密码");
        }
        if (uri.getRawQuery() != null) {
            throw new BusinessException(400, "Base URL 禁止包含 query 参数");
        }
        if (uri.getRawFragment() != null) {
            throw new BusinessException(400, "Base URL 禁止包含 fragment");
        }

        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            path = "/";
        } else if (!path.endsWith("/")) {
            path = path + "/";
        }

        try {
            URI normalized = new URI(
                    scheme.toLowerCase(Locale.ROOT),
                    null,
                    uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    path,
                    null,
                    null
            );
            return normalized.toString();
        } catch (URISyntaxException e) {
            throw new BusinessException(400, "Base URL 格式不正确", e);
        }
    }

    /**
     * 解析 URI，并把语法异常转换为业务异常。
     *
     * @param rawBaseUrl 原始 Base URL
     * @return URI 对象
     */
    private URI parse(String rawBaseUrl) {
        try {
            return new URI(rawBaseUrl);
        } catch (URISyntaxException e) {
            throw new BusinessException(400, "Base URL 格式不正确", e);
        }
    }
}
