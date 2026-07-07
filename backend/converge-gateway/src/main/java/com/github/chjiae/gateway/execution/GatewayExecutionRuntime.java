package com.github.chjiae.gateway.execution;

import com.github.chjiae.gateway.config.GatewayExecutionConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.PoolOptions;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网关上游执行运行时。
 * 负责维护共享 Vert.x HttpClient，并在关闭阶段拒绝新的数据面执行。
 */
public class GatewayExecutionRuntime {

    /** 执行配置 */
    private final GatewayExecutionConfig config;

    /** 网关构建版本 */
    private final String buildVersion;

    /** 共享上游 HTTP Client */
    private final HttpClientAgent httpClient;

    /** 是否处于排空关闭阶段 */
    private final AtomicBoolean draining = new AtomicBoolean(false);

    /**
     * 创建执行运行时。
     *
     * @param vertx Vert.x 实例
     * @param config 执行配置
     * @param buildVersion 构建版本
     */
    public GatewayExecutionRuntime(Vertx vertx, GatewayExecutionConfig config, String buildVersion) {
        this.config = config;
        this.buildVersion = buildVersion;
        HttpClientOptions options = new HttpClientOptions()
                .setConnectTimeout((int) config.upstreamConnectTimeoutMs())
                .setIdleTimeout((int) config.upstreamIdleTimeoutMs())
                .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)
                .setMaxRedirects(0);
        PoolOptions poolOptions = new PoolOptions()
                .setHttp1MaxSize(config.upstreamPoolMaxSize())
                .setHttp2MaxSize(config.upstreamPoolMaxSize());
        this.httpClient = vertx.createHttpClient(options, poolOptions);
    }

    /**
     * 进入排空阶段，后续新请求应返回 503。
     */
    public void beginDrain() {
        draining.set(true);
    }

    /**
     * 判断是否正在关闭排空。
     *
     * @return true 表示正在关闭
     */
    public boolean isDraining() {
        return draining.get();
    }

    /**
     * 获取执行配置。
     *
     * @return 执行配置
     */
    public GatewayExecutionConfig config() {
        return config;
    }

    /**
     * 获取共享 HTTP Client。
     *
     * @return HTTP Client
     */
    public HttpClientAgent httpClient() {
        return httpClient;
    }

    /**
     * 构造安全 User-Agent。
     *
     * @return User-Agent
     */
    public String userAgent() {
        return "converge-gateway/" + buildVersion;
    }

    /**
     * 关闭共享 HTTP Client。
     *
     * @return 关闭结果
     */
    public Future<Void> close() {
        return close(Duration.ofMillis(config.upstreamIdleTimeoutMs()));
    }

    /**
     * 按给定超时时间关闭共享 HTTP Client。
     *
     * @param timeout 关闭等待时间
     * @return 关闭结果
     */
    public Future<Void> close(Duration timeout) {
        beginDrain();
        return httpClient.close();
    }
}
