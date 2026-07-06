package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.enums.AiProviderKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 供应商更新请求。
 * 更新仍在当前租户范围内执行，不允许客户端修改 tenantId。
 */
@Data
public class AiProviderUpdateRequest {

    /** 租户内供应商编码，必填，支持字母、数字、下划线和短横线 */
    @NotBlank(message = "供应商编码不能为空")
    @Size(max = 64, message = "供应商编码长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "供应商编码只能包含字母、数字、下划线和短横线")
    private String code;

    /** 供应商展示名称，必填 */
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 128, message = "供应商名称长度不能超过 128")
    private String displayName;

    /** 供应商类型，必填 */
    @NotNull(message = "供应商类型不能为空")
    private AiProviderKind providerKind;

    /** 目录状态，必填 */
    @NotNull(message = "供应商状态不能为空")
    private AiCatalogStatus status;

    /** 供应商描述，可选 */
    @Size(max = 512, message = "供应商描述长度不能超过 512")
    private String description;
}
