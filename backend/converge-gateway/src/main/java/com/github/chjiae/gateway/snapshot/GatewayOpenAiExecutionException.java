package com.github.chjiae.gateway.snapshot;

/**
 * OpenAI 数据面执行目标解析异常。
 * 异常消息只包含安全错误分类，不包含 baseUrl、上游模型或 secret。
 */
public class GatewayOpenAiExecutionException extends RuntimeException {

    /** HTTP 状态码 */
    private final int statusCode;

    /** 数据面错误码 */
    private final String code;

    /** 安全错误消息 */
    private final String safeMessage;

    /**
     * 创建执行异常。
     *
     * @param statusCode HTTP 状态码
     * @param code 错误码
     * @param safeMessage 安全错误消息
     */
    public GatewayOpenAiExecutionException(int statusCode, String code, String safeMessage) {
        super(code);
        this.statusCode = statusCode;
        this.code = code;
        this.safeMessage = safeMessage;
    }

    /**
     * 获取 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public String code() {
        return code;
    }

    /**
     * 获取安全错误消息。
     *
     * @return 安全消息
     */
    public String safeMessage() {
        return safeMessage;
    }
}
