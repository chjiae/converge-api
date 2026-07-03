package com.github.chjiae.service.dto.user;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 分配角色请求参数。
 * 租户管理员为用户分配角色时使用。
 */
@Data
public class AssignRolesRequest {

    /** 角色 ID 列表（必填） */
    @NotNull(message = "角色 ID 列表不能为空")
    private List<Long> roleIds;
}
