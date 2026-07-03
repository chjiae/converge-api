package com.github.chjiae.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体，对应 role 表。
 * 表示系统中的一个角色定义，可以是系统内置角色或租户自定义角色。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("role")
public class Role extends BaseEntity {

    /** 角色编码（租户内唯一） */
    private String code;

    /** 角色名称 */
    private String name;

    /** 角色描述 */
    private String description;

    /** 是否系统内置角色（true 表示不可删除） */
    private Boolean isSystem;
}
