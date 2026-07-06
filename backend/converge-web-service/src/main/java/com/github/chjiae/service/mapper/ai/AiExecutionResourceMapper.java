package com.github.chjiae.service.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.ai.AiExecutionResource;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 可执行资源数据访问层
 */
@Mapper
public interface AiExecutionResourceMapper extends BaseMapper<AiExecutionResource> {
}
