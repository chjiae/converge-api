package com.github.chjiae.gateway.http;

import com.github.chjiae.gateway.config.GatewayConfig;
import com.github.chjiae.contract.gateway.GatewaySnapshotSyncState;
import com.github.chjiae.gateway.snapshot.GatewaySnapshotRuntime;
import com.github.chjiae.gateway.snapshot.GatewaySnapshotRuntimeStatus;
import com.github.chjiae.gateway.support.GatewayBuildInfo;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;

/**
 * 内部运维状态接口处理器。
 *
 * 本阶段不实现鉴权，`/internal/*` 只能部署在内部网络或受反向代理保护的环境中。
 */
public class InternalStatusHandler {

    /** 网关配置 */
    private final GatewayConfig config;

    /** 进程启动时间 */
    private final Instant startedAt;

    /** 网关快照运行时 */
    private final GatewaySnapshotRuntime snapshotRuntime;

    /**
     * 创建内部状态处理器。
     *
     * @param config 网关配置
     * @param startedAt 进程启动时间
     */
    public InternalStatusHandler(GatewayConfig config, Instant startedAt, GatewaySnapshotRuntime snapshotRuntime) {
        this.config = config;
        this.startedAt = startedAt;
        this.snapshotRuntime = snapshotRuntime;
    }

    /**
     * 处理存活检查。
     *
     * @param context 路由上下文
     */
    public void health(RoutingContext context) {
        JsonObject data = new JsonObject()
                .put("status", "UP")
                .put("service", config.serviceName());
        context.json(GatewayJsonResponses.success(RequestIdHandler.currentRequestId(context), data));
    }

    /**
     * 处理就绪检查。
     *
     * @param context 路由上下文
     */
    public void ready(RoutingContext context) {
        GatewaySnapshotRuntimeStatus status = snapshotRuntime.status();
        JsonObject data = new JsonObject()
                .put("status", status.state().name())
                .put("service", config.serviceName())
                .put("indexTenantCount", status.indexTenantCount())
                .put("loadedTenantCount", status.loadedTenantCount())
                .put("compiledRoutePlanCount", status.compiledRoutePlanCount())
                .put("invalidRouteTenantCount", status.invalidRouteTenantCount())
                .put("lastSuccessfulReconcileEpochMillis", status.lastSuccessfulReconcileEpochMillis())
                .put("latestErrorCategory", status.latestErrorCategory());
        if (status.state() == GatewaySnapshotSyncState.NOT_READY) {
            context.response().setStatusCode(503);
        }
        context.json(GatewayJsonResponses.success(RequestIdHandler.currentRequestId(context), data));
    }

    /**
     * 处理快照状态查询。
     * 响应只包含安全状态和计数，不暴露 Redis key、密文、nonce、HMAC 或 payload。
     *
     * @param context 路由上下文
     */
    public void snapshotStatus(RoutingContext context) {
        GatewaySnapshotRuntimeStatus status = snapshotRuntime.status();
        io.vertx.core.json.JsonArray tenants = new io.vertx.core.json.JsonArray();
        status.tenants().forEach(tenant -> tenants.add(new JsonObject()
                .put("tenantId", tenant.tenantId())
                .put("revision", tenant.revision())
                .put("schemaVersion", tenant.schemaVersion())
                .put("routePlanCount", tenant.routePlanCount())));
        JsonObject data = new JsonObject()
                .put("status", status.state().name())
                .put("indexTenantCount", status.indexTenantCount())
                .put("loadedTenantCount", status.loadedTenantCount())
                .put("compiledRoutePlanCount", status.compiledRoutePlanCount())
                .put("invalidRouteTenantCount", status.invalidRouteTenantCount())
                .put("lastSuccessfulReconcileEpochMillis", status.lastSuccessfulReconcileEpochMillis())
                .put("latestErrorCategory", status.latestErrorCategory())
                .put("tenants", tenants);
        context.json(GatewayJsonResponses.success(RequestIdHandler.currentRequestId(context), data));
    }

    /**
     * 处理版本信息查询。
     *
     * @param context 路由上下文
     */
    public void version(RoutingContext context) {
        JsonObject data = new JsonObject()
                .put("serviceName", config.serviceName())
                .put("buildVersion", config.buildVersion())
                .put("javaVersion", System.getProperty("java.version"))
                .put("vertxVersion", GatewayBuildInfo.vertxVersion())
                .put("startedAt", startedAt.toString());
        context.json(GatewayJsonResponses.success(RequestIdHandler.currentRequestId(context), data));
    }
}
