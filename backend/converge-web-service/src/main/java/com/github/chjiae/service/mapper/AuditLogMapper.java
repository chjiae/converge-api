package com.github.chjiae.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志数据访问层
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
