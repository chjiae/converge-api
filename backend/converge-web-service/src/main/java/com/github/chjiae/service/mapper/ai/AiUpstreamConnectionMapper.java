package com.github.chjiae.service.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.ai.AiUpstreamConnection;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 上游连接数据访问层。
 */
@Mapper
public interface AiUpstreamConnectionMapper extends BaseMapper<AiUpstreamConnection> {
}
