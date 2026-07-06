package com.github.chjiae.gateway.http;

import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 网关统一错误响应处理器。
 *
 * 404、500 与请求 ID 校验错误均使用相同 JSON 结构，且不输出请求体或敏感 Header。
 */
public final class GatewayErrorHandler {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    /** JSON 响应内容类型 */
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private GatewayErrorHandler() {
    }

    /**
     * 处理未匹配路由。
     *
     * @param context 路由上下文
     */
    public static void handleNotFound(RoutingContext context) {
        writeError(context, 404, "资源不存在");
    }

    /**
     * 处理未捕获异常。
     *
     * @param context 路由上下文
     */
    public static void handleFailure(RoutingContext context) {
        String requestId = RequestIdHandler.currentRequestId(context);
        Throwable failure = context.failure();
        if (failure != null) {
            log.error("网关处理异常，requestId={}", requestId, failure);
        } else {
            log.error("网关处理异常，requestId={}，未获取到异常对象", requestId);
        }
        writeError(context, 500, "网关内部错误");
    }

    /**
     * 写入错误响应。
     *
     * @param context 路由上下文
     * @param statusCode HTTP 状态码
     * @param message 错误消息
     */
    static void writeError(RoutingContext context, int statusCode, String message) {
        if (context.response().ended()) {
            return;
        }
        String requestId = RequestIdHandler.currentRequestId(context);
        context.response()
                .setStatusCode(statusCode)
                .putHeader("content-type", JSON_CONTENT_TYPE)
                .end(GatewayJsonResponses.error(statusCode, message, requestId).encode());
    }
}
