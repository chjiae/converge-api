package com.github.chjiae.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户-角色关联实体，对应 user_role 表。
 * 记录用户与角色之间的多对多关联关系。
 * 该表只有 id、user_id、role_id 三个字段，不继承 BaseEntity。
 */
@Data
@TableName("user_role")
public class UserRole implements Serializable {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 角色 ID */
    private Long roleId;
}
