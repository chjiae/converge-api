package com.github.chjiae.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 权限实体，对应 permission 表。
 * 表示系统中的一个权限定义，由资源类型和操作类型组合而成。
 * 该表没有 tenant_id、created_at、updated_at 字段，因此不继承 BaseEntity。
 */
@Data
@TableName("permission")
public class Permission implements Serializable {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限编码（全局唯一，如 tenant:create） */
    private String code;

    /** 权限名称 */
    private String name;

    /** 资源类型（tenant, user, role, subscription 等） */
    private String resource;

    /** 操作类型（create, read, update, delete, manage） */
    private String action;
}
