package com.github.chjiae.service.controller;

import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.dto.tenant.CreateTenantRequest;
import com.github.chjiae.service.dto.tenant.TenantResponse;
import com.github.chjiae.service.dto.tenant.UpdateTenantRequest;
import com.github.chjiae.service.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 租户管理控制器，提供租户 CRUD 和状态管理接口。
 * 所有操作均需 SUPER_ADMIN 角色。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class TenantController {

    /** 租户管理服务 */
    private final TenantService tenantService;

    /**
     * 创建租户（含首个管理员）
     *
     * @param request 创建租户请求参数
     * @return 创建的租户信息
     */
    @PostMapping
    public Result<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        log.info("创建租户接口调用，编码: {}", request.getCode());
        TenantResponse response = tenantService.createTenant(request);
        return Result.ok(response);
    }

    /**
     * 租户列表（分页）
     *
     * @param page 页码，默认 1
     * @param size 每页数量，默认 10
     * @return 分页租户列表
     */
    @GetMapping
    public Result<PageResult<TenantResponse>> listTenants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("租户列表接口调用，页码: {}，每页数量: {}", page, size);
        PageResult<TenantResponse> response = tenantService.listTenants(page, size);
        return Result.ok(response);
    }

    /**
     * 租户详情
     *
     * @param id 租户 ID
     * @return 租户详情
     */
    @GetMapping("/{id}")
    public Result<TenantResponse> getTenant(@PathVariable Long id) {
        log.info("租户详情接口调用，ID: {}", id);
        TenantResponse response = tenantService.getTenant(id);
        return Result.ok(response);
    }

    /**
     * 更新租户
     *
     * @param id      租户 ID
     * @param request 更新请求参数
     * @return 更新后的租户信息
     */
    @PutMapping("/{id}")
    public Result<TenantResponse> updateTenant(@PathVariable Long id, @RequestBody UpdateTenantRequest request) {
        log.info("更新租户接口调用，ID: {}", id);
        TenantResponse response = tenantService.updateTenant(id, request);
        return Result.ok(response);
    }

    /**
     * 删除租户（软删除）
     *
     * @param id 租户 ID
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTenant(@PathVariable Long id) {
        log.info("删除租户接口调用，ID: {}", id);
        tenantService.deleteTenant(id);
        return Result.ok();
    }

    /**
     * 启用租户
     *
     * @param id 租户 ID
     * @return 成功响应
     */
    @PostMapping("/{id}/enable")
    public Result<Void> enableTenant(@PathVariable Long id) {
        log.info("启用租户接口调用，ID: {}", id);
        tenantService.enableTenant(id);
        return Result.ok();
    }

    /**
     * 停用租户
     *
     * @param id 租户 ID
     * @return 成功响应
     */
    @PostMapping("/{id}/disable")
    public Result<Void> disableTenant(@PathVariable Long id) {
        log.info("停用租户接口调用，ID: {}", id);
        tenantService.disableTenant(id);
        return Result.ok();
    }
}
