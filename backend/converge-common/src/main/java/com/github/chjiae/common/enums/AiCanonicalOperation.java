package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 平台内部规范化操作类型。
 * 该枚举用于 PublicModel、模型绑定和静态路由策略的共同路由键。
 */
@Getter
@AllArgsConstructor
public enum AiCanonicalOperation {

    /** OpenAI Chat Completions 类操作 */
    CHAT_COMPLETIONS("Chat Completions"),

    /** OpenAI Responses 类操作 */
    RESPONSES("Responses"),

    /** Anthropic Messages 类操作 */
    MESSAGES("Messages"),

    /** 向量嵌入操作 */
    EMBEDDINGS("Embeddings"),

    /** 重排序操作 */
    RERANK("Rerank"),

    /** 图片生成操作 */
    IMAGE_GENERATION("Image Generation"),

    /** 语音合成操作 */
    AUDIO_SPEECH("Audio Speech"),

    /** 语音转写操作 */
    AUDIO_TRANSCRIPTION("Audio Transcription");

    /** 展示描述 */
    private final String description;
}
