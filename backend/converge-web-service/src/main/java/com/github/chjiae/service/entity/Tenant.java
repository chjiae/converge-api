package com.github.chjiae.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.TenantStatus;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户实体，对应 tenant 表。
 * 表示平台中的一个租户，包含租户基本信息和状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tenant")
public class Tenant extends BaseEntity {

    /** 租户 ID 在租户表中不存在，标记为非数据库字段以避免映射错误 */
    @TableField(exist = false)
    private Long tenantId;

    /** 租户编码（唯一，用于 URL/标识） */
    private String code;

    /** 租户名称 */
    private String name;

    /** 租户描述 */
    private String description;

    /** 租户状态：PENDING / TRIAL / ACTIVE / DISABLED / EXPIRED / DELETED */
    private TenantStatus status;

    /** 是否已使用过试用（每个租户只能试用一次） */
    private Boolean trialUsed;

    /** 到期时间（null 表示永不过期） */
    private LocalDateTime expiredAt;

    /** 创建人 ID（超管 ID 或自注册申请人 ID） */
    private Long createdBy;
}
