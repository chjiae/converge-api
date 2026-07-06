package com.github.chjiae.service.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 凭据创建请求。
 * 客户端仅传入管理元数据和明文 API Key，服务端负责加密、指纹计算和掩码生成。
 * tenantId、encryptedSecret、nonce、secretFingerprint、secretReference、encryptionKeyId
 * 均由服务端生成，客户端不得传入。
 */
@Data
public class AiCredentialCreateRequest {

    /** 凭据编码，必填，支持字母、数字、下划线和短横线 */
    @NotBlank(message = "凭据编码不能为空")
    @Size(max = 64, message = "凭据编码长度不能超过 64")
    @jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9_-]+$",
            message = "凭据编码只能包含字母、数字、下划线和短横线")
    private String code;

    /** 凭据展示名称，必填 */
    @NotBlank(message = "凭据名称不能为空")
    @Size(max = 128, message = "凭据名称长度不能超过 128")
    private String displayName;

    /** 凭据描述，可选 */
    @Size(max = 512, message = "凭据描述长度不能超过 512")
    private String description;

    /** 明文 API Key，必填，仅用于服务端加密存储，不会保存到数据库或返回响应 */
    @NotBlank(message = "API Key 不能为空")
    private String apiKey;
}
