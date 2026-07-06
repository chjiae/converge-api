package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 上游协议类型枚举，用于描述连接未来可适配的请求协议。
 */
@Getter
@AllArgsConstructor
public enum AiProtocolType {

    /** OpenAI 兼容协议 */
    OPENAI_COMPATIBLE("OpenAI 兼容协议"),
    /** Anthropic Messages 协议 */
    ANTHROPIC_MESSAGES("Anthropic Messages 协议"),
    /** Gemini Generative AI 协议 */
    GEMINI_GENERATIVE_AI("Gemini Generative AI 协议");

    /** 展示描述 */
    private final String description;
}
