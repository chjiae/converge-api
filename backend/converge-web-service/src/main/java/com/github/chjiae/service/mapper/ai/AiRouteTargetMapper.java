package com.github.chjiae.service.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.ai.AiRouteTarget;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 静态路由目标数据访问层。
 */
@Mapper
public interface AiRouteTargetMapper extends BaseMapper<AiRouteTarget> {
}
