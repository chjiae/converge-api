package com.github.chjiae.service.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 凭据更新请求（仅更新管理元数据，不变更 API Key）。
 * 如需变更 API Key，请使用轮换（rotate）接口。
 */
@Data
public class AiCredentialUpdateRequest {

    /** 凭据编码，必填 */
    @NotBlank(message = "凭据编码不能为空")
    @Size(max = 64, message = "凭据编码长度不能超过 64")
    @jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9_-]+$",
            message = "凭据编码只能包含字母、数字、下划线和短横线")
    private String code;

    /** 凭据展示名称，必填 */
    @NotBlank(message = "凭据名称不能为空")
    @Size(max = 128, message = "凭据名称长度不能超过 128")
    private String displayName;

    /** 凭据描述，可选 */
    @Size(max = 512, message = "凭据描述长度不能超过 512")
    private String description;
}
