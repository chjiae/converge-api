package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI Client API Key 访问组绑定创建请求。
 */
@Data
public class AiClientApiKeyAccessGroupCreateRequest {

    /** 访问组 ID，必填且必须属于当前租户 */
    @NotNull(message = "访问组 ID 不能为空")
    private Long accessGroupId;

    /** 管理状态，必填 */
    @NotNull(message = "绑定状态不能为空")
    private AiCatalogStatus adminStatus;
}
