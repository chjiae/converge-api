package com.github.chjiae.gateway;

import com.github.chjiae.gateway.config.GatewayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Converge 独立 Vert.x 网关入口。
 *
 * 该入口不依赖 Spring Boot、Servlet、数据库、Redis 或控制面模块，可作为独立 JVM 进程启动。
 */
public final class GatewayApplication {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(GatewayApplication.class);

    private GatewayApplication() {
    }

    /**
     * 启动网关进程。
     *
     * @param args 命令行参数，本阶段暂不使用
     */
    public static void main(String[] args) {
        GatewayConfig config;
        try {
            config = GatewayConfig.load();
        } catch (IllegalArgumentException e) {
            log.error("网关配置非法：{}", e.getMessage(), e);
            System.exit(1);
            return;
        }

        GatewayRuntime.start(config)
                .onSuccess(runtime -> Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(runtime, config),
                        "converge-gateway-shutdown")))
                .onFailure(throwable -> {
                    log.error("网关启动失败", throwable);
                    System.exit(1);
                });
    }

    /**
     * JVM 退出时优雅关闭网关。
     *
     * @param runtime 网关运行时
     * @param config 网关配置
     */
    private static void shutdown(GatewayRuntime runtime, GatewayConfig config) {
        try {
            log.info("收到 JVM 关闭信号，开始关闭网关");
            runtime.close()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(config.shutdownTimeoutMs() + 1000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("网关关闭过程异常", e);
        }
    }
}
