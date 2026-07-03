package com.github.chjiae.service.controller;

import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.common.result.Result;
import com.github.chjiae.service.dto.audit.AuditLogResponse;
import com.github.chjiae.service.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志控制器，提供审计日志的查询接口。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AuditLogController {

    /** 审计日志服务 */
    private final AuditLogService auditLogService;

    /**
     * 查询全平台审计日志（超管使用）
     *
     * @param page 页码，默认 1
     * @param size 每页数量，默认 10
     * @return 分页审计日志列表
     */
    @GetMapping("/api/v1/audit-logs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<PageResult<AuditLogResponse>> listAllAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询全平台审计日志接口调用，页码: {}，每页数量: {}", page, size);
        PageResult<AuditLogResponse> response = auditLogService.listAllAuditLogs(page, size);
        return Result.ok(response);
    }

    /**
     * 查询指定租户审计日志（超管使用）
     *
     * @param id   租户 ID
     * @param page 页码，默认 1
     * @param size 每页数量，默认 10
     * @return 分页审计日志列表
     */
    @GetMapping("/api/v1/tenants/{id}/audit-logs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<PageResult<AuditLogResponse>> listTenantAuditLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询租户审计日志接口调用，租户 ID: {}，页码: {}，每页数量: {}", id, page, size);
        PageResult<AuditLogResponse> response = auditLogService.listTenantAuditLogs(id, page, size);
        return Result.ok(response);
    }

    /**
     * 查询本租户审计日志（租户管理员/所有者使用）
     *
     * @param page 页码，默认 1
     * @param size 每页数量，默认 10
     * @return 分页审计日志列表
     */
    @GetMapping("/api/v1/my-audit-logs")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_OWNER')")
    public Result<PageResult<AuditLogResponse>> listMyAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询本租户审计日志接口调用，页码: {}，每页数量: {}", page, size);
        PageResult<AuditLogResponse> response = auditLogService.listMyAuditLogs(page, size);
        return Result.ok(response);
    }
}
