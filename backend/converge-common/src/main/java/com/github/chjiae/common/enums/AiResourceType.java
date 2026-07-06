package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 可执行资源类型枚举，本阶段仅支持 DIRECT_API。
 * 为未来 OAuth 代理、连接池等类型预留扩展点。
 */
@Getter
@AllArgsConstructor
public enum AiResourceType {

    /** 直接 API 调用资源（本阶段唯一支持的类型） */
    DIRECT_API("直接 API 调用");

    /** 展示描述 */
    private final String description;
}
