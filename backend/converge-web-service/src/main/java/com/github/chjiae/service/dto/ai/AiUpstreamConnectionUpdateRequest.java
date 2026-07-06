package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiProtocolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 上游连接更新请求。
 * providerId 必须属于当前租户，Service 层会显式校验。
 */
@Data
public class AiUpstreamConnectionUpdateRequest {

    /** 所属供应商 ID，必填，必须属于当前租户 */
    @NotNull(message = "供应商 ID 不能为空")
    private Long providerId;

    /** 供应商内连接编码，必填，支持字母、数字、下划线和短横线 */
    @NotBlank(message = "连接编码不能为空")
    @Size(max = 64, message = "连接编码长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "连接编码只能包含字母、数字、下划线和短横线")
    private String code;

    /** 连接展示名称，必填 */
    @NotBlank(message = "连接名称不能为空")
    @Size(max = 128, message = "连接名称长度不能超过 128")
    private String displayName;

    /** 上游协议类型，必填 */
    @NotNull(message = "连接协议类型不能为空")
    private AiProtocolType protocolType;

    /** 上游 Base URL，必填，仅允许绝对 http/https 地址 */
    @NotBlank(message = "Base URL 不能为空")
    @Size(max = 512, message = "Base URL 长度不能超过 512")
    private String baseUrl;

    /** 目录状态，必填 */
    @NotNull(message = "连接状态不能为空")
    private AiCatalogStatus status;

    /** 连接描述，可选 */
    @Size(max = 512, message = "连接描述长度不能超过 512")
    private String description;
}
