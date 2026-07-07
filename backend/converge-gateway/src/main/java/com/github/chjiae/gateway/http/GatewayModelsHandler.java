package com.github.chjiae.gateway.http;

import com.github.chjiae.contract.gateway.GatewayClientKeyAuthenticationResult;
import com.github.chjiae.contract.gateway.GatewayClientPrincipal;
import com.github.chjiae.contract.gateway.GatewaySnapshotSyncState;
import com.github.chjiae.gateway.snapshot.GatewaySnapshotRuntime;
import com.github.chjiae.gateway.snapshot.GatewaySnapshotRuntimeStatus;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

/**
 * `/v1/models` 数据面处理器。
 * 只基于本地快照认证 Client API Key 并返回授权模型，不访问 Redis、数据库或上游 HTTP。
 */
public class GatewayModelsHandler {

    /** 网关快照运行时 */
    private final GatewaySnapshotRuntime snapshotRuntime;

    /**
     * 创建模型列表处理器。
     *
     * @param snapshotRuntime 网关快照运行时
     */
    public GatewayModelsHandler(GatewaySnapshotRuntime snapshotRuntime) {
        this.snapshotRuntime = snapshotRuntime;
    }

    /**
     * 处理模型列表请求。
     *
     * @param context 路由上下文
     */
    public void handle(RoutingContext context) {
        GatewaySnapshotRuntimeStatus status = snapshotRuntime.status();
        if (status.state() == GatewaySnapshotSyncState.NOT_READY) {
            writeError(context, 503, "gateway_not_ready", "Gateway not ready");
            return;
        }

        String rawKey = extractBearerKey(context);
        GatewayClientKeyAuthenticationResult result = snapshotRuntime.authenticateClientKey(rawKey);
        if (!result.authenticated()) {
            context.response().putHeader("www-authenticate", "Bearer");
            writeError(context, 401, "invalid_api_key", "Invalid API key");
            return;
        }

        GatewayClientPrincipal principal = result.principal();
        if (principal.effectiveGrants().isEmpty()) {
            writeError(context, 403, "access_denied", "Access denied");
            return;
        }

        List<String> modelCodes = snapshotRuntime.authorizedModelCodes(principal);
        context.response()
                .setStatusCode(200)
                .putHeader("content-type", GatewayDataPlaneResponses.JSON_CONTENT_TYPE)
                .end(GatewayDataPlaneResponses.models(modelCodes).encode());
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

    private void writeError(RoutingContext context, int statusCode, String code, String message) {
        if (context.response().ended()) {
            return;
        }
        context.response()
                .setStatusCode(statusCode)
                .putHeader("content-type", GatewayDataPlaneResponses.JSON_CONTENT_TYPE)
                .end(GatewayDataPlaneResponses.error(code, message).encode());
    }
}
