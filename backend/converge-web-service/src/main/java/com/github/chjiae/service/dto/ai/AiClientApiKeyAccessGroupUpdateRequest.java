package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI Client API Key 访问组绑定更新请求。
 */
@Data
public class AiClientApiKeyAccessGroupUpdateRequest {

    /** 管理状态，必填 */
    @NotNull(message = "绑定状态不能为空")
    private AiCatalogStatus adminStatus;
}
