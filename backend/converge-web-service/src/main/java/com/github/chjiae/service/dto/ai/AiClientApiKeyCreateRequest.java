package com.github.chjiae.service.dto.ai;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

/**
 * AI Client API Key 创建请求。
 * raw key、keyId、salt、hash、tenantId 均由服务端生成或控制，客户端不可写。
 */
@Data
public class AiClientApiKeyCreateRequest {

    /** Key 编码，可选；为空时服务端根据展示名生成 */
    @Size(max = 64, message = "Key 编码长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Key 编码只能包含字母、数字、下划线和短横线")
    private String code;

    /** Key 展示名称，必填 */
    @Size(max = 128, message = "Key 名称长度不能超过 128")
    private String displayName;

    /** Key 描述，可选 */
    @Size(max = 512, message = "Key 描述长度不能超过 512")
    private String description;

    /** 过期时间，可选 */
    private Instant expiresAt;
}
