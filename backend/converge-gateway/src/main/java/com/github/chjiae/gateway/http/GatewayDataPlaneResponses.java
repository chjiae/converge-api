package com.github.chjiae.gateway.http;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * 网关数据面 OpenAI-compatible 响应构造工具。
 * 数据面错误格式与管理/内部接口错误格式分离。
 */
final class GatewayDataPlaneResponses {

    /** JSON 响应内容类型 */
    static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private GatewayDataPlaneResponses() {
    }

    /**
     * 构造 OpenAI-compatible 模型列表响应。
     *
     * @param modelCodes 模型编码
     * @return JSON 响应
     */
    static JsonObject models(List<String> modelCodes) {
        JsonArray data = new JsonArray();
        for (String modelCode : modelCodes) {
            data.add(new JsonObject()
                    .put("id", modelCode)
                    .put("object", "model")
                    .put("created", 0)
                    .put("owned_by", "converge"));
        }
        return new JsonObject()
                .put("object", "list")
                .put("data", data);
    }

    /**
     * 构造数据面错误响应。
     *
     * @param code 错误码
     * @param message 错误消息
     * @return JSON 响应
     */
    static JsonObject error(String code, String message) {
        return new JsonObject()
                .put("error", new JsonObject()
                        .put("message", message)
                        .put("type", "invalid_request_error")
                        .put("param", null)
                        .put("code", code));
    }
}
