package com.github.chjiae.service.dto.role;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/更新角色请求参数。
 * 租户管理员创建自定义角色或更新角色时使用。
 */
@Data
public class CreateRoleRequest {

    /** 角色编码（必填，租户内唯一） */
    @NotBlank(message = "角色编码不能为空")
    private String code;

    /** 角色名称（必填） */
    @NotBlank(message = "角色名称不能为空")
    private String name;

    /** 角色描述（可选） */
    private String description;
}
