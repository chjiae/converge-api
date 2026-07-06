package com.github.chjiae.service.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.ai.AiProvider;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 供应商数据访问层。
 */
@Mapper
public interface AiProviderMapper extends BaseMapper<AiProvider> {
}
