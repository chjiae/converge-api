package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 静态选择策略枚举。
 * 本阶段仅允许优先级加权，后续动态调度策略需单独设计。
 */
@Getter
@AllArgsConstructor
public enum AiSelectionPolicy {

    /** 高 priority 优先，同 priority 内按正整数 weight 选择 */
    PRIORITY_WEIGHTED("优先级加权");

    /** 展示描述 */
    private final String description;
}
