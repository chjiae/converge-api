package com.github.chjiae.gateway.http;

import com.github.chjiae.gateway.config.GatewayConfig;
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

    /**
     * 创建内部状态处理器。
     *
     * @param config 网关配置
     * @param startedAt 进程启动时间
     */
    public InternalStatusHandler(GatewayConfig config, Instant startedAt) {
        this.config = config;
        this.startedAt = startedAt;
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
     * 本阶段不依赖数据库、Redis 或上游服务，HTTP Server 成功启动即视为就绪。
     *
     * @param context 路由上下文
     */
    public void ready(RoutingContext context) {
        JsonObject data = new JsonObject()
                .put("status", "READY")
                .put("service", config.serviceName());
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
