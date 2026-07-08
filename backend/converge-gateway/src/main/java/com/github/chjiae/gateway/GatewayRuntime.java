package com.github.chjiae.gateway;

import com.github.chjiae.gateway.config.GatewayConfig;
import com.github.chjiae.gateway.execution.GatewayExecutionRuntime;
import com.github.chjiae.gateway.governance.GatewayRuntimeGovernanceRuntime;
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

    /** 网关上游执行运行时 */
    private GatewayExecutionRuntime executionRuntime;

    /** 网关运行时治理运行时 */
    private GatewayRuntimeGovernanceRuntime governanceRuntime;

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

        if (executionRuntime != null) {
            executionRuntime.beginDrain();
        }
        if (governanceRuntime != null) {
            governanceRuntime.beginDrain();
        }

        closeHttpServer()
                .compose(ignored -> closeExecutionRuntime())
                .compose(ignored -> closeGovernanceRuntime())
                .compose(ignored -> closeSnapshotRuntime())
                .compose(ignored -> vertx.close())
                .onComplete(result -> {
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
        executionRuntime = new GatewayExecutionRuntime(vertx, config.executionConfig(), config.buildVersion());
        governanceRuntime = new GatewayRuntimeGovernanceRuntime(vertx, config.snapshotConfig().redisUri(),
                config.runtimeGovernanceConfig());
        snapshotRuntime.start();
        Promise<GatewayRuntime> promise = Promise.promise();
        governanceRuntime.start()
                .compose(ignored -> {
                    Router router = GatewayRouterFactory.create(vertx, config, startedAt,
                            snapshotRuntime, executionRuntime, governanceRuntime, enableTestFailureRoute);
                    return vertx.createHttpServer()
                            .requestHandler(router)
                            .listen(config.port(), config.host());
                })
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

    private Future<Void> closeHttpServer() {
        if (server == null) {
            return Future.succeededFuture();
        }
        Promise<Void> promise = Promise.promise();
        AtomicBoolean completed = new AtomicBoolean(false);
        long timerId = vertx.setTimer(config.shutdownTimeoutMs(), ignored -> server.close().onComplete(result -> {
            if (completed.compareAndSet(false, true)) {
                if (result.failed()) {
                    log.warn("网关 HTTP Server 强制关闭异常，将继续释放后续资源", result.cause());
                }
                promise.complete();
            }
        }));
        server.shutdown(Duration.ofMillis(config.shutdownTimeoutMs()))
                .onComplete(result -> {
                    if (completed.compareAndSet(false, true)) {
                        vertx.cancelTimer(timerId);
                        if (result.failed()) {
                            log.warn("网关 HTTP Server 优雅关闭异常，将继续释放后续资源", result.cause());
                        }
                        promise.complete();
                    }
                });
        return promise.future();
    }

    private Future<Void> closeExecutionRuntime() {
        if (executionRuntime == null) {
            return Future.succeededFuture();
        }
        return executionRuntime.close(Duration.ofMillis(config.shutdownTimeoutMs()))
                .onFailure(throwable -> log.warn("网关执行运行时关闭异常，将继续释放后续资源", throwable))
                .recover(throwable -> Future.succeededFuture());
    }

    private Future<Void> closeGovernanceRuntime() {
        if (governanceRuntime == null) {
            return Future.succeededFuture();
        }
        return governanceRuntime.close()
                .onFailure(throwable -> log.warn("网关运行时治理关闭异常，将继续释放后续资源", throwable))
                .recover(throwable -> Future.succeededFuture());
    }

    private Future<Void> closeSnapshotRuntime() {
        if (snapshotRuntime == null) {
            return Future.succeededFuture();
        }
        return snapshotRuntime.close()
                .onFailure(throwable -> log.warn("网关快照运行时关闭异常，将继续释放 Vert.x", throwable))
                .recover(throwable -> Future.succeededFuture());
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
