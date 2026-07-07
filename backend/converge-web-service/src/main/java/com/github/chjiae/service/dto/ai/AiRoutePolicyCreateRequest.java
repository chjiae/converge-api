package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCanonicalOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 静态路由策略创建请求。
 * 新策略由服务端固定创建为 DRAFT，不允许客户端直接写 ENABLED。
 */
@Data
public class AiRoutePolicyCreateRequest {

    /** 公开模型 ID，必填且必须属于当前租户 */
    @NotNull(message = "公开模型 ID 不能为空")
    private Long publicModelId;

    /** 规范化操作类型，必填 */
    @NotNull(message = "规范化操作类型不能为空")
    private AiCanonicalOperation canonicalOperation;

    /** 策略展示名称，必填 */
    @NotBlank(message = "策略名称不能为空")
    @Size(max = 128, message = "策略名称长度不能超过 128")
    private String displayName;

    /** 策略描述，可选 */
    @Size(max = 512, message = "策略描述长度不能超过 512")
    private String description;
}
