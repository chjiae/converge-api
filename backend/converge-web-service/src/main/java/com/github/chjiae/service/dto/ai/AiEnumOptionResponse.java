package com.github.chjiae.service.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 管理台枚举选项响应。
 * 前端用于渲染表单选项，不暴露 Java 类名。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEnumOptionResponse {

    /** 枚举稳定名称。 */
    private String name;

    /** 页面展示标签。 */
    private String label;

    /** 选项说明。 */
    private String description;
}
