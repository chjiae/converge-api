package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 可执行资源管理状态枚举。
 * 相比 AiCatalogStatus 多了 DRAINING（排空中）状态，用于资源优雅下线。
 */
@Getter
@AllArgsConstructor
public enum AiResourceStatus {

    /** 已启用，可被调度器选用 */
    ENABLED("已启用"),

    /** 已停用，保留历史引用但不参与调度 */
    DISABLED("已停用"),

    /** 排空中，已有请求完成但不再接受新请求（为后续阶段预留） */
    DRAINING("排空中");

    /** 展示描述 */
    private final String description;
}
