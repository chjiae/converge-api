package com.github.chjiae.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限数据访问层
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
