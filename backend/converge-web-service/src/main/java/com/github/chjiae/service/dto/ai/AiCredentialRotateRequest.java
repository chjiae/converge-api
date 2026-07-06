package com.github.chjiae.service.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 凭据轮换请求。
 * 客户端提供新的明文 API Key，服务端重新加密、计算指纹和掩码，保留凭据 ID 不变。
 */
@Data
public class AiCredentialRotateRequest {

    /** 新的明文 API Key，必填，仅用于服务端加密存储 */
    @NotBlank(message = "新 API Key 不能为空")
    private String newApiKey;
}
