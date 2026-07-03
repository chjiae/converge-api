package com.github.chjiae.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色数据访问层
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
