package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCanonicalOperation;
import com.github.chjiae.common.enums.AiRoutePolicyStatus;
import com.github.chjiae.common.enums.AiSelectionPolicy;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 静态路由策略响应 DTO。
 */
@Data
@Builder
public class AiRoutePolicyResponse {

    /** 策略 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 公开模型 ID */
    private Long publicModelId;

    /** 规范化操作类型 */
    private AiCanonicalOperation canonicalOperation;

    /** 策略展示名称 */
    private String displayName;

    /** 策略描述 */
    private String description;

    /** 管理状态 */
    private AiRoutePolicyStatus adminStatus;

    /** 选择策略 */
    private AiSelectionPolicy selectionPolicy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
