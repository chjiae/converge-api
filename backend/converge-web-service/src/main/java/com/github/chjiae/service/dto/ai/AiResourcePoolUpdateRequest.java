package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 资源池更新请求。
 * 仅允许更新管理元数据，不允许写 tenantId 或内部快照字段。
 */
@Data
public class AiResourcePoolUpdateRequest {

    /** 资源池编码，必填且在租户内唯一 */
    @NotBlank(message = "资源池编码不能为空")
    @Size(max = 64, message = "资源池编码长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "资源池编码只能包含字母、数字、下划线和短横线")
    private String code;

    /** 资源池展示名称，必填 */
    @NotBlank(message = "资源池名称不能为空")
    @Size(max = 128, message = "资源池名称长度不能超过 128")
    private String displayName;

    /** 资源池描述，可选 */
    @Size(max = 512, message = "资源池描述长度不能超过 512")
    private String description;

    /** 管理状态，必填 */
    @NotNull(message = "资源池状态不能为空")
    private AiCatalogStatus adminStatus;
}
