package com.github.chjiae.service.dto.audit;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志响应 DTO，返回给前端的审计日志信息。
 */
@Data
@Builder
public class AuditLogResponse {

    /** 审计日志 ID */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 操作人 ID */
    private Long userId;

    /** 操作人用户名 */
    private String username;

    /** 模块名称 */
    private String module;

    /** 操作类型 */
    private String action;

    /** 操作对象 */
    private String target;

    /** 操作详情 */
    private String detail;

    /** 操作 IP 地址 */
    private String ipAddress;

    /** 客户端标识（User-Agent） */
    private String userAgent;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
