package com.github.chjiae.gateway.http;

import com.github.chjiae.gateway.governance.GatewayRuntimeGovernanceRuntime;
import com.github.chjiae.gateway.governance.GatewayRuntimeGovernanceStatus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * 运行时治理状态接口。
 * 仅输出安全计数和状态，不暴露 Redis key、leaseId、上游地址、模型名或任何秘密。
 */
class GatewayRuntimeStatusHandler {

    /** 运行时治理运行时 */
    private final GatewayRuntimeGovernanceRuntime governanceRuntime;

    /**
     * 创建状态处理器。
     *
     * @param governanceRuntime 运行时治理运行时
     */
    GatewayRuntimeStatusHandler(GatewayRuntimeGovernanceRuntime governanceRuntime) {
        this.governanceRuntime = governanceRuntime;
    }

    /**
     * 输出运行时治理状态。
     *
     * @param context 路由上下文
     */
    void handle(RoutingContext context) {
        GatewayRuntimeGovernanceStatus status = governanceRuntime.status();
        JsonObject body = new JsonObject()
                .put("state", status.state())
                .put("redisAvailable", status.redisAvailable())
                .put("activeLocalLeases", status.activeLocalLeases())
                .put("leaseAcquireGrantedCount", status.leaseAcquireGrantedCount())
                .put("leaseAcquireRejectedCount", status.leaseAcquireRejectedCount())
                .put("renewFailureCount", status.renewFailureCount())
                .put("runtimeStateUnavailableCount", status.runtimeStateUnavailableCount());
        context.response()
                .setStatusCode(200)
                .putHeader("content-type", GatewayDataPlaneResponses.JSON_CONTENT_TYPE)
                .end(body.encode());
    }
}
