package com.github.chjiae.service.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.ai.AiClientApiKeyAccessGroup;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI Client API Key 访问组绑定数据访问层。
 */
@Mapper
public interface AiClientApiKeyAccessGroupMapper extends BaseMapper<AiClientApiKeyAccessGroup> {
}
