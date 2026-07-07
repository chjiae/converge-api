package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCanonicalOperation;
import com.github.chjiae.common.enums.AiCatalogStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 访问组模型授权创建请求。
 * 仅支持精确 PublicModel + CanonicalOperation，不支持 wildcard、regex 或默认全量授权。
 */
@Data
public class AiAccessGroupModelGrantCreateRequest {

    /** 公开模型 ID，必填且必须属于当前租户 */
    @NotNull(message = "公开模型 ID 不能为空")
    private Long publicModelId;

    /** 规范化操作类型，必填 */
    @NotNull(message = "规范化操作类型不能为空")
    private AiCanonicalOperation canonicalOperation;

    /** 管理状态，必填 */
    @NotNull(message = "授权状态不能为空")
    private AiCatalogStatus adminStatus;
}
