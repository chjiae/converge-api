package com.github.chjiae.service.dto.role;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色响应 DTO，返回给前端的角色信息。
 */
@Data
@Builder
public class RoleResponse {

    /** 角色 ID */
    private Long id;

    /** 租户 ID（系统角色为 null） */
    private Long tenantId;

    /** 角色编码 */
    private String code;

    /** 角色名称 */
    private String name;

    /** 角色描述 */
    private String description;

    /** 是否系统内置角色 */
    private Boolean isSystem;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
