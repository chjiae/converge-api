package com.github.chjiae.service.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

/**
 * AI Client API Key 更新请求。
 * 只允许更新安全元数据，不允许写 raw key、keyId、salt、hash 或状态终态。
 */
@Data
public class AiClientApiKeyUpdateRequest {

    /** Key 编码，必填且在租户内唯一 */
    @NotBlank(message = "Key 编码不能为空")
    @Size(max = 64, message = "Key 编码长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Key 编码只能包含字母、数字、下划线和短横线")
    private String code;

    /** Key 展示名称，必填 */
    @NotBlank(message = "Key 名称不能为空")
    @Size(max = 128, message = "Key 名称长度不能超过 128")
    private String displayName;

    /** Key 描述，可选 */
    @Size(max = 512, message = "Key 描述长度不能超过 512")
    private String description;

    /** 过期时间，可选 */
    private Instant expiresAt;
}
