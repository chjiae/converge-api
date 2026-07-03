package com.github.chjiae.service.dto.tenant;

import lombok.Data;

/**
 * 更新租户请求参数。
 * 仅允许更新租户名称和描述。
 */
@Data
public class UpdateTenantRequest {

    /** 租户名称（可选，传则更新） */
    private String name;

    /** 租户描述（可选，传则更新） */
    private String description;
}
