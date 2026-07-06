package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 控制面目录状态枚举，用于启用或停用非敏感目录对象。
 */
@Getter
@AllArgsConstructor
public enum AiCatalogStatus {

    /** 已启用，可被后续阶段引用 */
    ENABLED("已启用"),
    /** 已停用，保留历史引用但不再作为可用配置 */
    DISABLED("已停用");

    /** 展示描述 */
    private final String description;
}
