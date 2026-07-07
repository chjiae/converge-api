package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 资源模型绑定更新请求。
 * 不允许修改 Resource、PublicModel 或 Operation，只允许调整上游模型名与状态。
 */
@Data
public class AiResourceModelBindingUpdateRequest {

    /** 精确上游模型名，必填，禁止 wildcard/regex */
    @NotBlank(message = "上游模型名称不能为空")
    @Size(max = 128, message = "上游模型名称长度不能超过 128")
    private String upstreamModelName;

    /** 管理状态，必填 */
    @NotNull(message = "绑定状态不能为空")
    private AiCatalogStatus adminStatus;
}
