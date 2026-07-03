package com.github.chjiae.service.dto.tenant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建租户请求参数。
 * 超管创建新租户时需要提供租户基本信息和首个管理员账号信息。
 */
@Data
public class CreateTenantRequest {

    /** 租户编码（必填，全局唯一，用于 URL/标识） */
    @NotBlank(message = "租户编码不能为空")
    private String code;

    /** 租户名称（必填） */
    @NotBlank(message = "租户名称不能为空")
    private String name;

    /** 租户描述（可选） */
    private String description;

    /** 管理员用户名（必填） */
    @NotBlank(message = "管理员用户名不能为空")
    private String adminUsername;

    /** 管理员邮箱（必填） */
    @NotBlank(message = "管理员邮箱不能为空")
    private String adminEmail;

    /** 管理员密码（必填） */
    @NotBlank(message = "管理员密码不能为空")
    private String adminPassword;
}
