package com.github.chjiae.service.dto.role;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 配置角色权限请求参数。
 * 为角色分配权限时使用。
 */
@Data
public class AssignPermissionsRequest {

    /** 权限 ID 列表（必填） */
    @NotNull(message = "权限 ID 列表不能为空")
    private List<Long> permissionIds;
}
