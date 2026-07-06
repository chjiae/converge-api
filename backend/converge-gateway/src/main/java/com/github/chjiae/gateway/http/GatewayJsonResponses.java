package com.github.chjiae.gateway.http;

import io.vertx.core.json.JsonObject;

import java.time.Instant;

/**
 * 网关 JSON 响应构造工具。
 *
 * 统一响应结构用于内部状态接口和错误响应，避免各 Handler 重复拼装字段。
 */
final class GatewayJsonResponses {

    private GatewayJsonResponses() {
    }

    /**
     * 构造成功响应。
     *
     * @param requestId 请求 ID
     * @param data 响应数据
     * @return JSON 响应
     */
    static JsonObject success(String requestId, JsonObject data) {
        return base(200, "OK", requestId)
                .put("data", data);
    }

    /**
     * 构造错误响应。
     *
     * @param statusCode HTTP 状态码
     * @param message 错误消息
     * @param requestId 请求 ID
     * @return JSON 响应
     */
    static JsonObject error(int statusCode, String message, String requestId) {
        return base(statusCode, message, requestId);
    }

    /**
     * 构造基础响应字段。
     *
     * @param code 响应码
     * @param message 响应消息
     * @param requestId 请求 ID
     * @return JSON 响应
     */
    private static JsonObject base(int code, String message, String requestId) {
        return new JsonObject()
                .put("code", code)
                .put("message", message)
                .put("requestId", requestId)
                .put("timestamp", Instant.now().toString());
    }
}
