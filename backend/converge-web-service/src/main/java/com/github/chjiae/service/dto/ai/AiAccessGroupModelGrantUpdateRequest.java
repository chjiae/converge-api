package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 访问组模型授权更新请求。
 * 授权对象不可改，如需调整模型或操作应新建另一条授权并停用旧授权。
 */
@Data
public class AiAccessGroupModelGrantUpdateRequest {

    /** 管理状态，必填 */
    @NotNull(message = "授权状态不能为空")
    private AiCatalogStatus adminStatus;
}
