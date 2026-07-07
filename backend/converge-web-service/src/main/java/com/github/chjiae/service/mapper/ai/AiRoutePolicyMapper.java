package com.github.chjiae.service.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.ai.AiRoutePolicy;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 静态路由策略数据访问层。
 */
@Mapper
public interface AiRoutePolicyMapper extends BaseMapper<AiRoutePolicy> {
}
