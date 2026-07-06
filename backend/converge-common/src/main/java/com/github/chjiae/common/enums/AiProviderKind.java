package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 供应商类型枚举，用于标识上游供应商的产品来源。
 */
@Getter
@AllArgsConstructor
public enum AiProviderKind {

    /** OpenAI 官方或等价供应商 */
    OPENAI("OpenAI"),
    /** Anthropic 官方或等价供应商 */
    ANTHROPIC("Anthropic"),
    /** Google Gemini 官方或等价供应商 */
    GOOGLE("Google"),
    /** xAI 官方或等价供应商 */
    XAI("xAI"),
    /** 自定义供应商 */
    CUSTOM("自定义");

    /** 展示描述 */
    private final String description;
}
