package com.github.chjiae.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户数据访问层
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
