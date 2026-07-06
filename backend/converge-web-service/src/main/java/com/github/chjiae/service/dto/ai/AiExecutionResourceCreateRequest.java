package com.github.chjiae.service.dto.ai;

import com.github.chjiae.common.enums.AiResourceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 可执行资源创建请求。
 * 客户端传入连接 ID、凭据 ID 和管理元数据，服务端校验四方一致性后创建静态绑定。
 * tenantId 由服务端根据当前租户上下文写入。
 */
@Data
public class AiExecutionResourceCreateRequest {

    /** 关联的上游连接 ID，必填 */
    @NotNull(message = "上游连接 ID 不能为空")
    private Long upstreamConnectionId;

    /** 关联的凭据 ID，必填 */
    @NotNull(message = "凭据 ID 不能为空")
    private Long credentialId;

    /** 资源编码，必填，支持字母、数字、下划线和短横线 */
    @NotBlank(message = "资源编码不能为空")
    @Size(max = 64, message = "资源编码长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "资源编码只能包含字母、数字、下划线和短横线")
    private String code;

    /** 资源展示名称，必填 */
    @NotBlank(message = "资源名称不能为空")
    @Size(max = 128, message = "资源名称长度不能超过 128")
    private String displayName;

    /** 资源描述，可选 */
    @Size(max = 512, message = "资源描述长度不能超过 512")
    private String description;

    /** 初始管理状态，必填 */
    @NotNull(message = "资源状态不能为空")
    private AiResourceStatus adminStatus;
}
