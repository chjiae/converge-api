package com.github.chjiae.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.TenantApplication;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户申请数据访问层
 */
@Mapper
public interface TenantApplicationMapper extends BaseMapper<TenantApplication> {
}
