package com.github.chjiae.service.controller;

import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.dto.role.AssignPermissionsRequest;
import com.github.chjiae.service.dto.role.CreateRoleRequest;
import com.github.chjiae.service.dto.role.RoleResponse;
import com.github.chjiae.service.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色权限管理控制器，提供角色 CRUD 和权限配置接口。
 * 所有操作均需租户管理员/所有者或超管角色。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_OWNER', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class RoleController {

    /** 角色权限管理服务 */
    private final RoleService roleService;

    /**
     * 角色列表（包含系统角色和当前租户角色）
     *
     * @return 角色列表
     */
    @GetMapping
    public Result<List<RoleResponse>> listRoles() {
        log.info("角色列表接口调用");
        List<RoleResponse> response = roleService.listRoles();
        return Result.ok(response);
    }

    /**
     * 创建自定义角色
     *
     * @param request 创建角色请求参数
     * @return 创建的角色信息
     */
    @PostMapping
    public Result<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        log.info("创建角色接口调用，编码: {}", request.getCode());
        RoleResponse response = roleService.createRole(request);
        return Result.ok(response);
    }

    /**
     * 更新角色（系统角色不可修改）
     *
     * @param id      角色 ID
     * @param request 更新请求参数
     * @return 更新后的角色信息
     */
    @PutMapping("/{id}")
    public Result<RoleResponse> updateRole(@PathVariable Long id, @Valid @RequestBody CreateRoleRequest request) {
        log.info("更新角色接口调用，角色 ID: {}", id);
        RoleResponse response = roleService.updateRole(id, request);
        return Result.ok(response);
    }

    /**
     * 删除角色（系统角色不可删除）
     *
     * @param id 角色 ID
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        log.info("删除角色接口调用，角色 ID: {}", id);
        roleService.deleteRole(id);
        return Result.ok();
    }

    /**
     * 配置角色权限（先删旧关联，再加新关联）
     *
     * @param id      角色 ID
     * @param request 配置权限请求参数
     * @return 成功响应
     */
    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody AssignPermissionsRequest request) {
        log.info("配置角色权限接口调用，角色 ID: {}，权限数量: {}", id, request.getPermissionIds().size());
        roleService.assignPermissions(id, request.getPermissionIds());
        return Result.ok();
    }
}
