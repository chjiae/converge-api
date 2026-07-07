package com.github.chjiae.service.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.ai.AiResourcePoolMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 资源池成员数据访问层。
 */
@Mapper
public interface AiResourcePoolMemberMapper extends BaseMapper<AiResourcePoolMember> {
}
