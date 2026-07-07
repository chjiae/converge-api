package com.github.chjiae.gateway.http;

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

    /** 数据面认证器 */
    private final GatewayDataPlaneAuthenticator authenticator;

    /**
     * 创建模型列表处理器。
     *
     * @param snapshotRuntime 网关快照运行时
     */
    public GatewayModelsHandler(GatewaySnapshotRuntime snapshotRuntime) {
        this.snapshotRuntime = snapshotRuntime;
        this.authenticator = new GatewayDataPlaneAuthenticator(snapshotRuntime);
    }

    /**
     * 处理模型列表请求。
     *
     * @param context 路由上下文
     */
    public void handle(RoutingContext context) {
        GatewaySnapshotRuntimeStatus status = snapshotRuntime.status();
        if (status.state() == GatewaySnapshotSyncState.NOT_READY) {
            GatewayDataPlaneResponses.writeError(context, 503, "gateway_not_ready", "Gateway not ready");
            return;
        }

        GatewayClientPrincipal principal = authenticator.authenticate(context);
        if (principal == null) {
            return;
        }
        if (principal.effectiveGrants().isEmpty()) {
            GatewayDataPlaneResponses.writeError(context, 403, "access_denied", "Access denied");
            return;
        }

        List<String> modelCodes = snapshotRuntime.authorizedModelCodes(principal);
        context.response()
                .setStatusCode(200)
                .putHeader("content-type", GatewayDataPlaneResponses.JSON_CONTENT_TYPE)
                .end(GatewayDataPlaneResponses.models(modelCodes).encode());
    }
}
