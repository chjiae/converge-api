package com.github.chjiae.gateway.http;

import com.github.chjiae.gateway.config.GatewayConfig;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

import java.time.Instant;

/**
 * 网关路由工厂。
 *
 * 当前阶段只注册内部运维接口和基础错误处理，不注册 `/v1/*` 或任何上游转发路由。
 */
public final class GatewayRouterFactory {

    private GatewayRouterFactory() {
    }

    /**
     * 创建正式运行路由。
     *
     * @param vertx Vert.x 实例
     * @param config 网关配置
     * @param startedAt 进程启动时间
     * @return 路由
     */
    public static Router create(Vertx vertx, GatewayConfig config, Instant startedAt) {
        return create(vertx, config, startedAt, false);
    }

    /**
     * 创建路由。
     *
     * @param vertx Vert.x 实例
     * @param config 网关配置
     * @param startedAt 进程启动时间
     * @param enableTestFailureRoute 是否启用测试专用异常路由
     * @return 路由
     */
    public static Router create(Vertx vertx, GatewayConfig config, Instant startedAt, boolean enableTestFailureRoute) {
        Router router = Router.router(vertx);
        InternalStatusHandler internalStatusHandler = new InternalStatusHandler(config, startedAt);

        router.route().handler(new AccessLogHandler());
        router.route().handler(new RequestIdHandler());

        router.get("/internal/health").handler(internalStatusHandler::health);
        router.get("/internal/ready").handler(internalStatusHandler::ready);
        router.get("/internal/version").handler(internalStatusHandler::version);

        if (enableTestFailureRoute) {
            // 仅测试统一 500 响应使用，正式运行配置不会注册此路由。
            router.get("/internal/test-error").handler(context -> context.fail(new IllegalStateException("测试异常")));
        }

        router.route().failureHandler(GatewayErrorHandler::handleFailure);
        router.route().handler(GatewayErrorHandler::handleNotFound);
        return router;
    }
}
