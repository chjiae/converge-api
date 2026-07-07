package com.github.chjiae.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 下游 Client API Key 状态枚举。
 * REVOKED 为终态，不能重新启用或轮换。
 */
@Getter
@AllArgsConstructor
public enum AiClientApiKeyStatus {

    /** 已启用，可被网关认证 */
    ENABLED("已启用"),

    /** 已停用，可重新启用 */
    DISABLED("已停用"),

    /** 已撤销，终态 */
    REVOKED("已撤销");

    /** 展示描述 */
    private final String description;
}
