package com.github.chjiae.gateway.http;

/**
 * OpenAI SSE 重写异常。
 * 异常只表达安全分类，不携带 event 内容。
 */
class GatewayOpenAiSseException extends RuntimeException {

    /**
     * 创建 SSE 异常。
     *
     * @param message 安全消息
     */
    GatewayOpenAiSseException(String message) {
        super(message);
    }
}
