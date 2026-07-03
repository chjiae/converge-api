package com.github.chjiae.service.controller;

import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.dto.user.*;
import com.github.chjiae.service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理控制器，提供租户内用户的 CRUD、状态管理和角色分配接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    /** 用户管理服务 */
    private final UserService userService;

    /**
     * 获取当前登录用户信息
     *
     * @return 当前用户信息
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<UserResponse> getCurrentUser() {
        log.info("获取当前用户信息接口调用");
        UserResponse response = userService.getCurrentUser();
        return Result.ok(response);
    }

    /**
     * 更新当前用户信息（手机号、邮箱）
     *
     * @param request 更新请求参数
     * @return 更新后的用户信息
     */
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<UserResponse> updateCurrentUser(@RequestBody UpdateUserRequest request) {
        log.info("更新当前用户信息接口调用");
        UserResponse response = userService.updateCurrentUser(request);
        return Result.ok(response);
    }

    /**
     * 修改当前用户密码
     *
     * @param body 包含 oldPassword 和 newPassword 的请求体
     * @return 成功响应
     */
    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        log.info("修改密码接口调用");
        userService.changePassword(oldPassword, newPassword);
        return Result.ok();
    }

    /**
     * 用户列表（分页），仅租户管理员/所有者可访问
     *
     * @param page 页码，默认 1
     * @param size 每页数量，默认 10
     * @return 分页用户列表
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_OWNER')")
    public Result<PageResult<UserResponse>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("用户列表接口调用，页码: {}，每页数量: {}", page, size);
        PageResult<UserResponse> response = userService.listUsers(page, size);
        return Result.ok(response);
    }

    /**
     * 创建用户（租户管理员/所有者操作）
     *
     * @param request 创建用户请求参数
     * @return 创建的用户信息
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_OWNER')")
    public Result<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("创建用户接口调用，用户名: {}", request.getUsername());
        UserResponse response = userService.createUser(request);
        return Result.ok(response);
    }

    /**
     * 删除用户（租户管理员/所有者操作，不能删除自己）
     *
     * @param id 用户 ID
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_OWNER')")
    public Result<Void> deleteUser(@PathVariable Long id) {
        log.info("删除用户接口调用，用户 ID: {}", id);
        userService.deleteUser(id);
        return Result.ok();
    }

    /**
     * 启用/停用用户
     *
     * @param id  用户 ID
     * @param body 包含 status 的请求体（ACTIVE / DISABLED）
     * @return 更新后的用户信息
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_OWNER')")
    public Result<UserResponse> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        log.info("更新用户状态接口调用，用户 ID: {}，目标状态: {}", id, status);
        UserResponse response = userService.updateUserStatus(id, status);
        return Result.ok(response);
    }

    /**
     * 为用户分配角色
     *
     * @param id      用户 ID
     * @param request 分配角色请求参数
     * @return 更新后的用户信息
     */
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_OWNER')")
    public Result<UserResponse> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        log.info("分配用户角色接口调用，用户 ID: {}，角色数量: {}", id, request.getRoleIds().size());
        UserResponse response = userService.assignRoles(id, request.getRoleIds());
        return Result.ok(response);
    }
}
