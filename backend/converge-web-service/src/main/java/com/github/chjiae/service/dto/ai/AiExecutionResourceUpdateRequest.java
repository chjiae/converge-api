package com.github.chjiae.service.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 可执行资源更新请求。
 * 仅允许更新管理元数据（编码、名称、描述），不允许变更绑定关系
 * （connectionId、credentialId、providerId 不可修改）。
 */
@Data
public class AiExecutionResourceUpdateRequest {

    /** 资源编码，必填 */
    @NotBlank(message = "资源编码不能为空")
    @Size(max = 64, message = "资源编码长度不能超过 64")
    @jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9_-]+$",
            message = "资源编码只能包含字母、数字、下划线和短横线")
    private String code;

    /** 资源展示名称，必填 */
    @NotBlank(message = "资源名称不能为空")
    @Size(max = 128, message = "资源名称长度不能超过 128")
    private String displayName;

    /** 资源描述，可选 */
    @Size(max = 512, message = "资源描述长度不能超过 512")
    private String description;
}
