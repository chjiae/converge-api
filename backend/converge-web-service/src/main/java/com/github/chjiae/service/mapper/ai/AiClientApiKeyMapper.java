package com.github.chjiae.service.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.ai.AiClientApiKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI Client API Key 数据访问层。
 */
@Mapper
public interface AiClientApiKeyMapper extends BaseMapper<AiClientApiKey> {
}
