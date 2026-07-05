package com.github.chjiae.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.SubscriptionPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订阅套餐配置数据访问层。
 */
@Mapper
public interface SubscriptionPlanMapper extends BaseMapper<SubscriptionPlan> {
}
