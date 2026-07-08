package com.github.chjiae.service.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI Gateway Chat Completions 测试消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGatewayChatMessageRequest {

    /** OpenAI 兼容消息角色。 */
    @NotBlank(message = "消息角色不能为空")
    private String role;

    /** 消息内容，仅用于本次测试转发，不落库、不写日志。 */
    @NotBlank(message = "消息内容不能为空")
    private String content;
}
