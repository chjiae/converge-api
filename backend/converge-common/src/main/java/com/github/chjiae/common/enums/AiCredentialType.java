package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 凭据类型枚举，本阶段仅支持 API_KEY。
 * 为未来 OAuth、Service Account 等类型预留扩展点。
 */
@Getter
@AllArgsConstructor
public enum AiCredentialType {

    /** API Key 凭据（本阶段唯一支持的类型） */
    API_KEY("API Key");

    /** 展示描述 */
    private final String description;
}
