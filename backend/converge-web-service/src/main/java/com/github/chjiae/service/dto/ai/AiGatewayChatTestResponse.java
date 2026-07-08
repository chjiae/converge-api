package com.github.chjiae.service.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AI Gateway Chat Completions 非流式测试响应。
 * 成功和错误都统一包装，避免前端直接处理 Gateway 原始错误格式。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGatewayChatTestResponse {

    /** Gateway 返回的 HTTP 状态码。 */
    private int status;

    /** 本次代理调用耗时，单位毫秒。 */
    private long latencyMs;

    /** Gateway 成功响应 JSON，错误时为空。 */
    private Map<String, Object> data;

    /** 规范化错误码，成功时为空。 */
    private String errorCode;

    /** 用户可读错误消息，成功时为空。 */
    private String errorMessage;
}
