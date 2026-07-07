package com.github.chjiae.service.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 静态路由策略更新请求。
 * 不允许修改 PublicModel、Operation、tenantId 或内部快照字段。
 */
@Data
public class AiRoutePolicyUpdateRequest {

    /** 策略展示名称，必填 */
    @NotBlank(message = "策略名称不能为空")
    @Size(max = 128, message = "策略名称长度不能超过 128")
    private String displayName;

    /** 策略描述，可选 */
    @Size(max = 512, message = "策略描述长度不能超过 512")
    private String description;
}
