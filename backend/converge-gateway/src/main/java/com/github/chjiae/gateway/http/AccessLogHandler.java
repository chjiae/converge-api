package com.github.chjiae.gateway.http;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 最小访问日志处理器。
 *
 * 日志只记录方法、路径、状态码、耗时和 requestId；不读取请求 Body，不记录 Authorization、Cookie 或其他敏感 Header。
 */
public class AccessLogHandler implements Handler<RoutingContext> {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(AccessLogHandler.class);

    @Override
    public void handle(RoutingContext context) {
        long startedAtNanos = System.nanoTime();
        context.addBodyEndHandler(ignored -> {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
            String requestId = RequestIdHandler.currentRequestId(context);
            log.info("网关访问日志：requestId={}，method={}，path={}，status={}，durationMs={}",
                    requestId,
                    context.request().method().name(),
                    context.request().path(),
                    context.response().getStatusCode(),
                    durationMs);
        });
        context.next();
    }
}
