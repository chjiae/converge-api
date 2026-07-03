package com.github.chjiae.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色-权限关联实体，对应 role_permission 表。
 * 记录角色与权限之间的多对多关联关系。
 * 该表只有 id、role_id、permission_id 三个字段，不继承 BaseEntity。
 */
@Data
@TableName("role_permission")
public class RolePermission implements Serializable {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色 ID */
    private Long roleId;

    /** 权限 ID */
    private Long permissionId;
}
