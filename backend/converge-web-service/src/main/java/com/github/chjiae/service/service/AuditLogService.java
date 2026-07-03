package com.github.chjiae.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.chjiae.common.result.PageResult;
import com.github.chjiae.service.dto.audit.AuditLogResponse;
import com.github.chjiae.service.entity.AuditLog;
import com.github.chjiae.service.mapper.AuditLogMapper;
import com.github.chjiae.service.security.UserPrincipal;
import com.github.chjiae.service.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审计日志服务，记录和查询系统操作审计日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    /** 审计日志数据访问层 */
    private final AuditLogMapper auditLogMapper;

    /**
     * 记录审计日志
     *
     * @param module  模块名称（auth, user, role, tenant, subscription 等）
     * @param action  操作类型（create, update, delete, login, logout 等）
     * @param target  操作对象（如 "user:123", "role:456"）
     * @param detail  操作详情（JSON 字符串）
     * @param request 当前 HTTP 请求（获取 IP 和 UserAgent）
     */
    public void log(String module, String action, String target, String detail, HttpServletRequest request) {
        AuditLog auditLog = new AuditLog();
        auditLog.setModule(module);
        auditLog.setAction(action);
        auditLog.setTarget(target);
        auditLog.setDetail(detail);
        auditLog.setCreatedAt(LocalDateTime.now());

        // 从请求中获取 IP 和 UserAgent
        if (request != null) {
            auditLog.setIpAddress(getClientIp(request));
            auditLog.setUserAgent(request.getHeader("User-Agent"));
        }

        // 从 SecurityContext 获取当前用户信息
        UserPrincipal principal = getCurrentUser();
        if (principal != null) {
            auditLog.setUserId(principal.getUserId());
            auditLog.setUsername(principal.getUsername());
            auditLog.setTenantId(principal.getTenantId());
        }

        // audit_log 在忽略列表中，不受租户过滤影响，直接插入
        auditLogMapper.insert(auditLog);
        log.info("审计日志已记录：模块={}, 操作={}, 目标={}, 用户={}", module, action, target,
                principal != null ? principal.getUsername() : "anonymous");
    }

    /**
     * 记录审计日志（简化版，无 HTTP 请求）
     *
     * @param module 模块名称
     * @param action 操作类型
     * @param target 操作对象
     * @param detail 操作详情
     */
    public void log(String module, String action, String target, String detail) {
        log(module, action, target, detail, null);
    }

    /**
     * 查询全平台审计日志（超管使用）
     *
     * @param page 页码（从 1 开始）
     * @param size 每页数量
     * @return 分页审计日志列表
     */
    public PageResult<AuditLogResponse> listAllAuditLogs(int page, int size) {
        log.info("查询全平台审计日志，页码: {}，每页数量: {}", page, size);

        Page<AuditLog> pageParam = new Page<>(page, size);
        Page<AuditLog> resultPage = auditLogMapper.selectPage(pageParam,
                new LambdaQueryWrapper<AuditLog>()
                        .orderByDesc(AuditLog::getCreatedAt)
        );

        List<AuditLogResponse> list = resultPage.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(list, resultPage.getTotal(), page, size);
    }

    /**
     * 查询指定租户审计日志（超管使用）
     *
     * @param tenantId 租户 ID
     * @param page     页码（从 1 开始）
     * @param size     每页数量
     * @return 分页审计日志列表
     */
    public PageResult<AuditLogResponse> listTenantAuditLogs(Long tenantId, int page, int size) {
        log.info("查询租户审计日志，租户 ID: {}，页码: {}，每页数量: {}", tenantId, page, size);

        Page<AuditLog> pageParam = new Page<>(page, size);
        Page<AuditLog> resultPage = auditLogMapper.selectPage(pageParam,
                new LambdaQueryWrapper<AuditLog>()
                        .eq(AuditLog::getTenantId, tenantId)
                        .orderByDesc(AuditLog::getCreatedAt)
        );

        List<AuditLogResponse> list = resultPage.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(list, resultPage.getTotal(), page, size);
    }

    /**
     * 查询本租户审计日志（租户管理员使用）
     * 自动使用 TenantContext 中的 tenantId
     *
     * @param page 页码（从 1 开始）
     * @param size 每页数量
     * @return 分页审计日志列表
     */
    public PageResult<AuditLogResponse> listMyAuditLogs(int page, int size) {
        Long tenantId = TenantContext.getTenantId();
        log.info("查询本租户审计日志，租户 ID: {}，页码: {}，每页数量: {}", tenantId, page, size);

        Page<AuditLog> pageParam = new Page<>(page, size);
        Page<AuditLog> resultPage = auditLogMapper.selectPage(pageParam,
                new LambdaQueryWrapper<AuditLog>()
                        .eq(AuditLog::getTenantId, tenantId)
                        .orderByDesc(AuditLog::getCreatedAt)
        );

        List<AuditLogResponse> list = resultPage.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(list, resultPage.getTotal(), page, size);
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 当前用户主体，未登录时返回 null
     */
    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }

    /**
     * 获取客户端真实 IP 地址
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 将审计日志实体转换为响应 DTO
     *
     * @param entity 审计日志实体
     * @return 审计日志响应 DTO
     */
    private AuditLogResponse toResponse(AuditLog entity) {
        return AuditLogResponse.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .module(entity.getModule())
                .action(entity.getAction())
                .target(entity.getTarget())
                .detail(entity.getDetail())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
