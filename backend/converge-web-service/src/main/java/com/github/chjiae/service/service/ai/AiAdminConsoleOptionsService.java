package com.github.chjiae.service.service.ai;

import com.github.chjiae.common.enums.AiCanonicalOperation;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiClientApiKeyStatus;
import com.github.chjiae.common.enums.AiCredentialType;
import com.github.chjiae.common.enums.AiProtocolType;
import com.github.chjiae.common.enums.AiProviderKind;
import com.github.chjiae.common.enums.AiResourceStatus;
import com.github.chjiae.common.enums.AiRoutePolicyStatus;
import com.github.chjiae.common.enums.AiSelectionPolicy;
import com.github.chjiae.service.dto.ai.AiEnumOptionResponse;
import com.github.chjiae.service.dto.ai.AiOptionsResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 管理台枚举选项服务。
 */
@Service
public class AiAdminConsoleOptionsService {

    /**
     * 获取 AI 管理台全部枚举选项。
     *
     * @return 选项集合
     */
    public AiOptionsResponse getOptions() {
        return AiOptionsResponse.builder()
                .providerKinds(providerKinds())
                .protocolTypes(protocolTypes())
                .catalogStatuses(catalogStatuses())
                .resourceStatuses(resourceStatuses())
                .credentialTypes(credentialTypes())
                .canonicalOperations(canonicalOperations())
                .selectionPolicies(selectionPolicies())
                .routePolicyStatuses(routePolicyStatuses())
                .clientApiKeyStatuses(clientApiKeyStatuses())
                .build();
    }

    private List<AiEnumOptionResponse> providerKinds() {
        List<AiEnumOptionResponse> options = new ArrayList<>();
        for (AiProviderKind value : AiProviderKind.values()) {
            options.add(option(value.name(), value.getDescription(), providerDescription(value)));
        }
        return options;
    }

    private List<AiEnumOptionResponse> protocolTypes() {
        List<AiEnumOptionResponse> options = new ArrayList<>();
        for (AiProtocolType value : AiProtocolType.values()) {
            options.add(option(value.name(), value.getDescription(), value.getDescription()));
        }
        return options;
    }

    private List<AiEnumOptionResponse> catalogStatuses() {
        List<AiEnumOptionResponse> options = new ArrayList<>();
        for (AiCatalogStatus value : AiCatalogStatus.values()) {
            options.add(option(value.name(), value.getDescription(), value.getDescription()));
        }
        return options;
    }

    private List<AiEnumOptionResponse> resourceStatuses() {
        List<AiEnumOptionResponse> options = new ArrayList<>();
        for (AiResourceStatus value : AiResourceStatus.values()) {
            options.add(option(value.name(), value.getDescription(), value.getDescription()));
        }
        return options;
    }

    private List<AiEnumOptionResponse> credentialTypes() {
        List<AiEnumOptionResponse> options = new ArrayList<>();
        for (AiCredentialType value : AiCredentialType.values()) {
            options.add(option(value.name(), value.getDescription(), value.getDescription()));
        }
        return options;
    }

    private List<AiEnumOptionResponse> canonicalOperations() {
        List<AiEnumOptionResponse> options = new ArrayList<>();
        for (AiCanonicalOperation value : AiCanonicalOperation.values()) {
            options.add(option(value.name(), value.getDescription(), value.getDescription()));
        }
        return options;
    }

    private List<AiEnumOptionResponse> selectionPolicies() {
        List<AiEnumOptionResponse> options = new ArrayList<>();
        for (AiSelectionPolicy value : AiSelectionPolicy.values()) {
            options.add(option(value.name(), value.getDescription(), value.getDescription()));
        }
        return options;
    }

    private List<AiEnumOptionResponse> routePolicyStatuses() {
        List<AiEnumOptionResponse> options = new ArrayList<>();
        for (AiRoutePolicyStatus value : AiRoutePolicyStatus.values()) {
            options.add(option(value.name(), value.getDescription(), value.getDescription()));
        }
        return options;
    }

    private List<AiEnumOptionResponse> clientApiKeyStatuses() {
        List<AiEnumOptionResponse> options = new ArrayList<>();
        for (AiClientApiKeyStatus value : AiClientApiKeyStatus.values()) {
            options.add(option(value.name(), value.getDescription(), value.getDescription()));
        }
        return options;
    }

    private AiEnumOptionResponse option(String name, String label, String description) {
        return AiEnumOptionResponse.builder()
                .name(name)
                .label(label)
                .description(description)
                .build();
    }

    private String providerDescription(AiProviderKind value) {
        if (value == AiProviderKind.OPENAI) {
            return "OpenAI 官方或等价供应商";
        }
        if (value == AiProviderKind.ANTHROPIC) {
            return "Anthropic 官方或等价供应商";
        }
        if (value == AiProviderKind.GOOGLE) {
            return "Google Gemini 官方或等价供应商";
        }
        if (value == AiProviderKind.XAI) {
            return "xAI 官方或等价供应商";
        }
        return "自定义 OpenAI 兼容供应商";
    }
}
