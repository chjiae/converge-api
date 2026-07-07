package com.github.chjiae.gateway.config;

/**
 * 网关上游执行配置。
 *
 * @param upstreamConnectTimeoutMs 上游连接超时时间，单位毫秒
 * @param upstreamIdleTimeoutMs 上游空闲超时时间，单位毫秒
 * @param upstreamPoolMaxSize 上游共享连接池最大连接数
 * @param openAiMaxRequestBytes OpenAI 请求体最大字节数
 * @param openAiMaxNonStreamResponseBytes OpenAI 非流式响应最大字节数
 * @param openAiMaxErrorResponseBytes 上游错误响应最大读取字节数
 * @param openAiMaxSseEventBytes 单个 SSE event 最大字节数
 */
public record GatewayExecutionConfig(
        long upstreamConnectTimeoutMs,
        long upstreamIdleTimeoutMs,
        int upstreamPoolMaxSize,
        long openAiMaxRequestBytes,
        long openAiMaxNonStreamResponseBytes,
        long openAiMaxErrorResponseBytes,
        long openAiMaxSseEventBytes
) {

    /** 默认上游连接超时 */
    public static final long DEFAULT_UPSTREAM_CONNECT_TIMEOUT_MS = 10000L;

    /** 默认上游空闲超时 */
    public static final long DEFAULT_UPSTREAM_IDLE_TIMEOUT_MS = 90000L;

    /** 默认上游连接池大小 */
    public static final int DEFAULT_UPSTREAM_POOL_MAX_SIZE = 100;

    /** 默认请求体上限 */
    public static final long DEFAULT_OPENAI_MAX_REQUEST_BYTES = 4_194_304L;

    /** 默认非流式响应上限 */
    public static final long DEFAULT_OPENAI_MAX_NON_STREAM_RESPONSE_BYTES = 16_777_216L;

    /** 默认上游错误响应读取上限 */
    public static final long DEFAULT_OPENAI_MAX_ERROR_RESPONSE_BYTES = 65_536L;

    /** 默认单 SSE event 上限 */
    public static final long DEFAULT_OPENAI_MAX_SSE_EVENT_BYTES = 1_048_576L;

    /**
     * 构造执行配置并进行启动期校验。
     */
    public GatewayExecutionConfig {
        validatePositive(upstreamConnectTimeoutMs, "网关上游连接超时时间必须大于 0 毫秒");
        validatePositive(upstreamIdleTimeoutMs, "网关上游空闲超时时间必须大于 0 毫秒");
        validatePositive(upstreamPoolMaxSize, "网关上游连接池大小必须大于 0");
        validatePositive(openAiMaxRequestBytes, "OpenAI 请求体上限必须大于 0");
        validatePositive(openAiMaxNonStreamResponseBytes, "OpenAI 非流式响应上限必须大于 0");
        validatePositive(openAiMaxErrorResponseBytes, "OpenAI 错误响应读取上限必须大于 0");
        validatePositive(openAiMaxSseEventBytes, "OpenAI SSE event 上限必须大于 0");
        if (upstreamConnectTimeoutMs > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("网关上游连接超时时间不能超过 Integer.MAX_VALUE 毫秒");
        }
        if (upstreamIdleTimeoutMs > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("网关上游空闲超时时间不能超过 Integer.MAX_VALUE 毫秒");
        }
    }

    /**
     * 创建默认执行配置。
     *
     * @return 默认配置
     */
    public static GatewayExecutionConfig defaults() {
        return new GatewayExecutionConfig(DEFAULT_UPSTREAM_CONNECT_TIMEOUT_MS,
                DEFAULT_UPSTREAM_IDLE_TIMEOUT_MS,
                DEFAULT_UPSTREAM_POOL_MAX_SIZE,
                DEFAULT_OPENAI_MAX_REQUEST_BYTES,
                DEFAULT_OPENAI_MAX_NON_STREAM_RESPONSE_BYTES,
                DEFAULT_OPENAI_MAX_ERROR_RESPONSE_BYTES,
                DEFAULT_OPENAI_MAX_SSE_EVENT_BYTES);
    }

    private static void validatePositive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
