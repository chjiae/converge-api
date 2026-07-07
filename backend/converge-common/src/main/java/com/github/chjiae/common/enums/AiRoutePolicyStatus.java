package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 静态路由策略状态。
 * 策略默认草稿，只有拓扑校验通过后才能启用。
 */
@Getter
@AllArgsConstructor
public enum AiRoutePolicyStatus {

    /** 草稿状态，不进入网关静态路由计划 */
    DRAFT("草稿"),

    /** 已启用，可进入网关静态路由计划 */
    ENABLED("已启用"),

    /** 已停用，保留历史但不参与静态候选 */
    DISABLED("已停用");

    /** 展示描述 */
    private final String description;
}
