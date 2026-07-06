package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 公开模型创建请求。
 * code 是下游稳定别名，不要求等同于任何上游模型名称。
 */
@Data
public class AiPublicModelCreateRequest {

    /** 下游公开模型别名，必填，支持字母、数字、点、下划线和短横线 */
    @NotBlank(message = "公开模型编码不能为空")
    @Size(max = 64, message = "公开模型编码长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "公开模型编码只能包含字母、数字、点、下划线和短横线")
    private String code;

    /** 公开模型展示名称，必填 */
    @NotBlank(message = "公开模型名称不能为空")
    @Size(max = 128, message = "公开模型名称长度不能超过 128")
    private String displayName;

    /** 模型族或产品线，必填 */
    @NotBlank(message = "模型族不能为空")
    @Size(max = 64, message = "模型族长度不能超过 64")
    private String modelFamily;

    /** 目录状态，必填 */
    @NotNull(message = "公开模型状态不能为空")
    private AiCatalogStatus status;

    /** 公开模型描述，可选 */
    @Size(max = 512, message = "公开模型描述长度不能超过 512")
    private String description;
}
