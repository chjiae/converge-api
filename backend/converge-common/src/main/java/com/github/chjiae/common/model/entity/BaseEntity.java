package com.github.chjiae.common.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类，包含公共审计字段和租户标识。
 * 所有持久化实体应继承此类。
 */
@Data
public abstract class BaseEntity implements Serializable {

    /** 主键 ID */
    private Long id;

    /** 租户 ID（超管/平台级数据为 null） */
    private Long tenantId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
