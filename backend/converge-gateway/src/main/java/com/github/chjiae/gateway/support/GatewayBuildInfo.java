package com.github.chjiae.gateway.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 网关构建信息读取器。
 *
 * 该类只读取打包时由 Maven 过滤生成的资源文件，不访问外部配置中心或数据库。
 */
public final class GatewayBuildInfo {

    /** 构建信息资源路径 */
    private static final String RESOURCE_NAME = "gateway-version.properties";

    /** 构建版本兜底值 */
    private static final String DEFAULT_BUILD_VERSION = "1.0.0-SNAPSHOT";

    /** Vert.x 版本兜底值 */
    private static final String DEFAULT_VERTX_VERSION = "5.1.3";

    /** 缓存后的构建信息 */
    private static final Properties PROPERTIES = loadProperties();

    private GatewayBuildInfo() {
    }

    /**
     * 获取 Maven 项目版本。
     *
     * @return 构建版本
     */
    public static String buildVersion() {
        return PROPERTIES.getProperty("gateway.build.version", DEFAULT_BUILD_VERSION);
    }

    /**
     * 获取 Vert.x 依赖版本。
     *
     * @return Vert.x 版本
     */
    public static String vertxVersion() {
        return PROPERTIES.getProperty("gateway.vertx.version", DEFAULT_VERTX_VERSION);
    }

    /**
     * 读取构建信息资源。
     *
     * @return 构建信息属性
     */
    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream inputStream = GatewayBuildInfo.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException ignored) {
            // 构建信息缺失不影响进程启动，版本接口会使用兜底值。
        }
        return properties;
    }
}
