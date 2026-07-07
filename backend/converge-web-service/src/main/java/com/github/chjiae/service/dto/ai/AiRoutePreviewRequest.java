package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCanonicalOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 静态路由预览请求。
 * 预览只使用静态配置，不执行上游请求，也不应用动态限流、健康或会话状态。
 */
@Data
public class AiRoutePreviewRequest {

    /** 公开模型编码，必填 */
    @NotBlank(message = "公开模型编码不能为空")
    @Size(max = 64, message = "公开模型编码长度不能超过 64")
    private String publicModelCode;

    /** 规范化操作类型，必填 */
    @NotNull(message = "规范化操作类型不能为空")
    private AiCanonicalOperation canonicalOperation;

    /** 可选确定性预览 seed */
    @Size(max = 128, message = "预览 seed 长度不能超过 128")
    private String selectionSeed;
}
