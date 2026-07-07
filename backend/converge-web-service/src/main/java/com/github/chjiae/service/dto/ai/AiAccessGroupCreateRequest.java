package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiCatalogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 访问组创建请求。
 * tenantId 与快照字段均由服务端控制，客户端不可写。
 */
@Data
public class AiAccessGroupCreateRequest {

    /** 访问组编码，必填且在租户内唯一 */
    @NotBlank(message = "访问组编码不能为空")
    @Size(max = 64, message = "访问组编码长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "访问组编码只能包含字母、数字、下划线和短横线")
    private String code;

    /** 访问组展示名称，必填 */
    @NotBlank(message = "访问组名称不能为空")
    @Size(max = 128, message = "访问组名称长度不能超过 128")
    private String displayName;

    /** 访问组描述，可选 */
    @Size(max = 512, message = "访问组描述长度不能超过 512")
    private String description;

    /** 管理状态，必填 */
    @NotNull(message = "访问组状态不能为空")
    private AiCatalogStatus adminStatus;
}
