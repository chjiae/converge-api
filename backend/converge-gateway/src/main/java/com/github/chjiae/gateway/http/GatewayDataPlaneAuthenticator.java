package com.github.chjiae.gateway.http;

import com.github.chjiae.contract.gateway.GatewayClientKeyAuthenticationResult;
import com.github.chjiae.contract.gateway.GatewayClientPrincipal;
import com.github.chjiae.gateway.snapshot.GatewaySnapshotRuntime;
import io.vertx.ext.web.RoutingContext;

/**
 * 数据面 Client API Key 认证器。
 * 只做严格 Bearer 解析与本地快照认证，不把 raw key 写入上下文。
 */
public class GatewayDataPlaneAuthenticator {

    /** 网关快照运行时 */
    private final GatewaySnapshotRuntime snapshotRuntime;

    /**
     * 创建认证器。
     *
     * @param snapshotRuntime 快照运行时
     */
    public GatewayDataPlaneAuthenticator(GatewaySnapshotRuntime snapshotRuntime) {
        this.snapshotRuntime = snapshotRuntime;
    }

    /**
     * 认证当前请求，失败时直接写入 401。
     *
     * @param context 路由上下文
     * @return 认证主体，失败时返回 null
     */
    public GatewayClientPrincipal authenticate(RoutingContext context) {
        String rawKey = extractBearerKey(context);
        GatewayClientKeyAuthenticationResult result = snapshotRuntime.authenticateClientKey(rawKey);
        if (!result.authenticated()) {
            context.response().putHeader("www-authenticate", "Bearer");
            GatewayDataPlaneResponses.writeError(context, 401, "invalid_api_key", "Invalid API key");
            return null;
        }
        return result.principal();
    }

    private String extractBearerKey(RoutingContext context) {
        String authorization = context.request().getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String rawKey = authorization.substring("Bearer ".length());
        if (rawKey.isBlank() || rawKey.contains(" ")) {
            return null;
        }
        return rawKey;
    }
}
