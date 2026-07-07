package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCanonicalOperation;
import com.github.chjiae.common.enums.AiCatalogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 资源模型绑定创建请求。
 * 表达 Resource + PublicModel + Operation 到精确上游模型名的绑定。
 */
@Data
public class AiResourceModelBindingCreateRequest {

    /** 执行资源 ID，必填且必须属于当前租户 */
    @NotNull(message = "执行资源 ID 不能为空")
    private Long executionResourceId;

    /** 公开模型 ID，必填且必须属于当前租户 */
    @NotNull(message = "公开模型 ID 不能为空")
    private Long publicModelId;

    /** 规范化操作类型，必填 */
    @NotNull(message = "规范化操作类型不能为空")
    private AiCanonicalOperation canonicalOperation;

    /** 精确上游模型名，必填，禁止 wildcard/regex */
    @NotBlank(message = "上游模型名称不能为空")
    @Size(max = 128, message = "上游模型名称长度不能超过 128")
    private String upstreamModelName;

    /** 管理状态，必填 */
    @NotNull(message = "绑定状态不能为空")
    private AiCatalogStatus adminStatus;
}
