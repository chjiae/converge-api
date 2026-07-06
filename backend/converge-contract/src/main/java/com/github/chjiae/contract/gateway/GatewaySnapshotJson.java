package com.github.chjiae.contract.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;

/**
 * 网关快照 JSON 序列化工具。
 * 控制面和网关共用同一 ObjectMapper 配置，确保契约序列化兼容。
 */
public final class GatewaySnapshotJson {

    /** 共享 JSON 序列化器。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private GatewaySnapshotJson() {
    }

    /**
     * 序列化对象为 JSON 字符串。
     *
     * @param value 契约对象
     * @return JSON 字符串
     */
    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("网关快照 JSON 序列化失败", e);
        }
    }

    /**
     * 序列化对象为 UTF-8 JSON 字节。
     *
     * @param value 契约对象
     * @return JSON 字节
     */
    public static byte[] toBytes(Object value) {
        return toJson(value).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 从 JSON 字符串反序列化契约对象。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T> 目标类型
     * @return 契约对象
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("网关快照 JSON 解析失败", e);
        }
    }

    /**
     * 从 UTF-8 JSON 字节反序列化契约对象。
     *
     * @param json JSON 字节
     * @param type 目标类型
     * @param <T> 目标类型
     * @return 契约对象
     */
    public static <T> T fromBytes(byte[] json, Class<T> type) {
        return fromJson(new String(json, StandardCharsets.UTF_8), type);
    }
}
