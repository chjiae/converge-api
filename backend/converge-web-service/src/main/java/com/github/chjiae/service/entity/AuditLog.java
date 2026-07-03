package com.github.chjiae.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志实体，对应 audit_log 表。
 * 记录系统中用户的操作行为，用于安全审计和行为追踪。
 * 该表有 tenant_id 但没有 updated_at 字段，因此不继承 BaseEntity，自定义所需字段。
 */
@Data
@TableName("audit_log")
public class AuditLog implements Serializable {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID（平台级操作为 null） */
    private Long tenantId;

    /** 操作人 ID */
    private Long userId;

    /** 操作人用户名（冗余存储，防止用户删除后丢失审计信息） */
    private String username;

    /** 模块名称（auth, user, role, tenant, subscription 等） */
    private String module;

    /** 操作类型（create, update, delete, login, logout 等） */
    private String action;

    /** 操作对象（如 user:123, role:456） */
    private String target;

    /** 操作详情（JSON 格式，记录变更前后对比等） */
    private String detail;

    /** 操作 IP 地址 */
    private String ipAddress;

    /** 客户端标识（User-Agent） */
    private String userAgent;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
