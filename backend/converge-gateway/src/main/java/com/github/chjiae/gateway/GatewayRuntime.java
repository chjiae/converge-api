package com.github.chjiae.gateway;

import com.github.chjiae.gateway.config.GatewayConfig;
import com.github.chjiae.gateway.http.GatewayRouterFactory;
import com.github.chjiae.gateway.snapshot.GatewaySnapshotRuntime;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网关运行时。
 *
 * 负责创建 Vert.x、启动 HTTP Server，并在关闭时先停止 HTTP Server 再释放 Vert.x。
 */
public class GatewayRuntime {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(GatewayRuntime.class);

    /** Vert.x 实例 */
    private final Vertx vertx;

    /** 网关配置 */
    private final GatewayConfig config;

    /** 进程启动时间 */
    private final Instant startedAt;

    /** 是否启用测试异常路由 */
    private final boolean enableTestFailureRoute;

    /** 关闭状态 */
    private final AtomicBoolean closing = new AtomicBoolean(false);

    /** HTTP Server */
    private HttpServer server;

    /** 网关快照运行时 */
    private GatewaySnapshotRuntime snapshotRuntime;

    private GatewayRuntime(Vertx vertx, GatewayConfig config, Instant startedAt, boolean enableTestFailureRoute) {
        this.vertx = vertx;
        this.config = config;
        this.startedAt = startedAt;
        this.enableTestFailureRoute = enableTestFailureRoute;
    }

    /**
     * 启动正式网关运行时。
     *
     * @param config 网关配置
     * @return 启动后的运行时
     */
    public static Future<GatewayRuntime> start(GatewayConfig config) {
        return start(config, false);
    }

    /**
     * 启动网关运行时。
     *
     * @param config 网关配置
     * @param enableTestFailureRoute 是否启用测试异常路由
     * @return 启动后的运行时
     */
    public static Future<GatewayRuntime> start(GatewayConfig config, boolean enableTestFailureRoute) {
        Vertx vertx = Vertx.vertx();
        GatewayRuntime runtime = new GatewayRuntime(vertx, config, Instant.now(), enableTestFailureRoute);
        return runtime.startHttpServer()
                .otherwise(throwable -> {
                    vertx.close();
                    throw new IllegalStateException("网关启动失败", throwable);
                });
    }

    /**
     * 获取实际监听端口。
     *
     * @return 实际监听端口
     */
    public int actualPort() {
        if (server == null) {
            return -1;
        }
        return server.actualPort();
    }

    /**
     * 关闭网关运行时。
     *
     * @return 关闭结果
     */
    public Future<Void> close() {
        if (!closing.compareAndSet(false, true)) {
            return Future.succeededFuture();
        }

        log.info("开始关闭网关运行时");
        Promise<Void> closePromise = Promise.promise();
        AtomicBoolean completed = new AtomicBoolean(false);
        ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "converge-gateway-close-timeout");
            thread.setDaemon(true);
            return thread;
        });
        timeoutScheduler.schedule(() -> {
            if (completed.compareAndSet(false, true)) {
                log.warn("网关关闭超过 {} 毫秒，强制释放 Vert.x", config.shutdownTimeoutMs());
                vertx.close().onComplete(result -> completePromise(closePromise, timeoutScheduler, result.succeeded(), result.cause()));
            }
        }, config.shutdownTimeoutMs() + 1000, TimeUnit.MILLISECONDS);

        if (server != null) {
            server.shutdown(Duration.ofMillis(config.shutdownTimeoutMs()))
                    .onFailure(throwable -> log.warn("网关 HTTP Server 优雅关闭异常，将继续释放 Vert.x", throwable));
        }
        if (snapshotRuntime != null) {
            snapshotRuntime.close()
                    .onFailure(throwable -> log.warn("网关快照运行时关闭异常，将继续释放 Vert.x", throwable));
        }
        vertx.close().onComplete(result -> {
            if (completed.compareAndSet(false, true)) {
                completePromise(closePromise, timeoutScheduler, result.succeeded(), result.cause());
            }
        });
        return closePromise.future();
    }

    /**
     * 启动 HTTP Server。
     *
     * @return 启动后的运行时
     */
    private Future<GatewayRuntime> startHttpServer() {
        snapshotRuntime = new GatewaySnapshotRuntime(vertx, config.snapshotConfig());
        snapshotRuntime.start();
        Router router = GatewayRouterFactory.create(vertx, config, startedAt, snapshotRuntime, enableTestFailureRoute);
        Promise<GatewayRuntime> promise = Promise.promise();
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(config.port(), config.host())
                .onSuccess(startedServer -> {
                    server = startedServer;
                    log.info("网关 HTTP Server 已启动，监听地址: {}:{}，服务名: {}，版本: {}",
                            config.host(), startedServer.actualPort(), config.serviceName(), config.buildVersion());
                    promise.complete(this);
                })
                .onFailure(throwable -> {
                    log.error("网关 HTTP Server 启动失败，监听地址: {}:{}",
                            config.host(), config.port(), throwable);
                    promise.fail(throwable);
                });
        return promise.future();
    }

    /**
     * 完成关闭 Promise。
     *
     * @param promise 关闭 Promise
     * @param timeoutScheduler 超时兜底调度器
     * @param succeeded 是否成功
     * @param throwable 失败原因
     */
    private void completePromise(Promise<Void> promise, ScheduledExecutorService timeoutScheduler,
                                 boolean succeeded, Throwable throwable) {
        timeoutScheduler.shutdownNow();
        if (succeeded) {
            log.info("网关运行时已关闭");
            promise.complete();
        } else {
            log.error("网关运行时关闭失败", throwable);
            promise.fail(throwable);
        }
    }
}
