package com.github.chjiae.service.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 管理台表单枚举集合响应。
 * 用于避免前端硬编码控制面枚举标签。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOptionsResponse {

    /** Provider 类型选项。 */
    private List<AiEnumOptionResponse> providerKinds;

    /** 上游协议类型选项。 */
    private List<AiEnumOptionResponse> protocolTypes;

    /** 通用目录状态选项。 */
    private List<AiEnumOptionResponse> catalogStatuses;

    /** 执行资源状态选项。 */
    private List<AiEnumOptionResponse> resourceStatuses;

    /** 凭据类型选项。 */
    private List<AiEnumOptionResponse> credentialTypes;

    /** 规范化操作类型选项。 */
    private List<AiEnumOptionResponse> canonicalOperations;

    /** 静态选择策略选项。 */
    private List<AiEnumOptionResponse> selectionPolicies;

    /** 路由策略状态选项。 */
    private List<AiEnumOptionResponse> routePolicyStatuses;

    /** Client API Key 状态选项。 */
    private List<AiEnumOptionResponse> clientApiKeyStatuses;
}
