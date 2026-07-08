package com.github.chjiae.service.dto.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI Gateway Chat Completions 非流式测试请求。
 * Client API Key 只用于本次请求转发，禁止落库和写入日志。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGatewayChatTestRequest {

    /** 本次测试使用的 Client API Key。 */
    @NotBlank(message = "Client API Key 不能为空")
    private String clientApiKey;

    /** 公开模型编码。 */
    @NotBlank(message = "模型不能为空")
    private String model;

    /** OpenAI 兼容消息列表。 */
    @NotEmpty(message = "消息列表不能为空")
    private List<@Valid AiGatewayChatMessageRequest> messages;

    /** 采样温度，可为空。 */
    private Double temperature;

    /** 最大输出 token 数，可为空。 */
    private Integer maxTokens;
}
