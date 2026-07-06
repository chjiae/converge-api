package com.github.chjiae.gateway.config;

import com.github.chjiae.gateway.support.GatewayBuildInfo;

import java.util.Map;

/**
 * 网关运行配置。
 *
 * 配置只从系统属性和环境变量读取，不接入 Spring 配置体系，确保网关可作为独立 JVM 进程运行。
 *
 * @param host              HTTP 监听地址
 * @param port              HTTP 监听端口，测试场景允许 0 表示随机端口
 * @param shutdownTimeoutMs 优雅关闭等待时间，单位毫秒
 * @param serviceName       服务名称
 * @param buildVersion      构建版本
 * @param snapshotConfig    快照同步配置
 */
public record GatewayConfig(
        String host,
        int port,
        long shutdownTimeoutMs,
        String serviceName,
        String buildVersion,
        GatewaySnapshotConfig snapshotConfig
) {

    /** 默认监听地址 */
    private static final String DEFAULT_HOST = "0.0.0.0";

    /** 默认监听端口 */
    private static final int DEFAULT_PORT = 8081;

    /** 默认优雅关闭超时时间 */
    private static final long DEFAULT_SHUTDOWN_TIMEOUT_MS = 10000L;

    /** 默认服务名 */
    private static final String DEFAULT_SERVICE_NAME = "converge-gateway";

    /**
     * 构造网关配置并进行启动期校验。
     */
    public GatewayConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("网关监听地址不能为空");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("网关监听端口必须在 0 到 65535 之间");
        }
        if (shutdownTimeoutMs <= 0) {
            throw new IllegalArgumentException("网关优雅关闭超时时间必须大于 0 毫秒");
        }
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("网关服务名不能为空");
        }
        if (buildVersion == null || buildVersion.isBlank()) {
            throw new IllegalArgumentException("网关构建版本不能为空");
        }
        if (snapshotConfig == null) {
            throw new IllegalArgumentException("网关快照配置不能为空");
        }
    }

    /**
     * 从当前 JVM 系统属性和环境变量加载配置。
     *
     * @return 网关配置
     */
    public static GatewayConfig load() {
        return load(System.getProperties(), System.getenv());
    }

    /**
     * 从给定属性和环境变量加载配置，便于测试配置解析逻辑。
     *
     * 系统属性同时支持大写配置键和点分小写键，例如 `GATEWAY_PORT` 与 `gateway.port`。
     *
     * @param properties JVM 系统属性
     * @param environment 环境变量
     * @return 网关配置
     */
    public static GatewayConfig load(Map<?, ?> properties, Map<String, String> environment) {
        String host = read(properties, environment, "GATEWAY_HOST", "gateway.host", DEFAULT_HOST);
        int port = parsePort(read(properties, environment, "GATEWAY_PORT", "gateway.port", String.valueOf(DEFAULT_PORT)));
        long shutdownTimeoutMs = parsePositiveLong(
                read(properties, environment, "GATEWAY_SHUTDOWN_TIMEOUT_MS", "gateway.shutdown-timeout-ms",
                        String.valueOf(DEFAULT_SHUTDOWN_TIMEOUT_MS)),
                "网关优雅关闭超时时间必须是大于 0 的整数");
        String serviceName = read(properties, environment, "GATEWAY_SERVICE_NAME", "gateway.service-name", DEFAULT_SERVICE_NAME);
        String buildVersion = read(properties, environment, "GATEWAY_BUILD_VERSION", "gateway.build-version",
                GatewayBuildInfo.buildVersion());
        GatewaySnapshotConfig snapshotConfig = new GatewaySnapshotConfig(
                read(properties, environment, "GATEWAY_REDIS_URI", "gateway.redis-uri", ""),
                read(properties, environment, "AI_GATEWAY_SNAPSHOT_KEY_ID", "ai.gateway.snapshot.key-id", ""),
                read(properties, environment, "AI_GATEWAY_SNAPSHOT_ENCRYPTION_KEY_BASE64",
                        "ai.gateway.snapshot.encryption-key-base64", ""),
                read(properties, environment, "AI_GATEWAY_SNAPSHOT_SIGNING_KEY_BASE64",
                        "ai.gateway.snapshot.signing-key-base64", ""),
                parsePositiveLong(read(properties, environment, "GATEWAY_SNAPSHOT_RECONCILE_INTERVAL_MS",
                                "gateway.snapshot.reconcile-interval-ms", "5000"),
                        "网关快照对账间隔必须是大于 0 的整数"),
                parsePositiveLong(read(properties, environment, "GATEWAY_SNAPSHOT_MAX_STALENESS_MS",
                                "gateway.snapshot.max-staleness-ms", "30000"),
                        "网关快照最大陈旧时间必须是大于 0 的整数"),
                (int) parsePositiveLong(read(properties, environment, "GATEWAY_SNAPSHOT_HISTORY_RETAIN_COUNT",
                                "gateway.snapshot.history-retain-count", "3"),
                        "网关快照历史保留数量必须是大于 0 的整数")
        );
        return new GatewayConfig(host, port, shutdownTimeoutMs, serviceName, buildVersion, snapshotConfig);
    }

    /**
     * 读取配置值，优先级为系统属性大写键、系统属性小写键、环境变量、默认值。
     *
     * @param properties JVM 系统属性
     * @param environment 环境变量
     * @param envKey 环境变量键
     * @param propertyKey 系统属性键
     * @param defaultValue 默认值
     * @return 配置值
     */
    private static String read(Map<?, ?> properties, Map<String, String> environment,
                               String envKey, String propertyKey, String defaultValue) {
        String upperPropertyValue = valueOf(properties.get(envKey));
        if (!upperPropertyValue.isBlank()) {
            return upperPropertyValue;
        }
        String propertyValue = valueOf(properties.get(propertyKey));
        if (!propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = valueOf(environment.get(envKey));
        if (!envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }

    /**
     * 解析端口配置。
     *
     * @param value 端口字符串
     * @return 端口
     */
    private static int parsePort(String value) {
        long port = parsePositiveLongAllowZero(value, "网关监听端口必须是 0 到 65535 之间的整数");
        if (port > 65535) {
            throw new IllegalArgumentException("网关监听端口必须在 0 到 65535 之间");
        }
        return (int) port;
    }

    /**
     * 解析大于 0 的长整数。
     *
     * @param value 配置字符串
     * @param errorMessage 错误信息
     * @return 长整数
     */
    private static long parsePositiveLong(String value, String errorMessage) {
        long parsed = parsePositiveLongAllowZero(value, errorMessage);
        if (parsed <= 0) {
            throw new IllegalArgumentException(errorMessage);
        }
        return parsed;
    }

    /**
     * 解析非负长整数。
     *
     * @param value 配置字符串
     * @param errorMessage 错误信息
     * @return 长整数
     */
    private static long parsePositiveLongAllowZero(String value, String errorMessage) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(errorMessage);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(errorMessage, e);
        }
    }

    /**
     * 将对象安全转换为字符串。
     *
     * @param value 原始值
     * @return 字符串，null 时返回空串
     */
    private static String valueOf(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
