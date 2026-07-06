package com.github.chjiae.service.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.ai.AiPublicModel;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 公开模型数据访问层。
 */
@Mapper
public interface AiPublicModelMapper extends BaseMapper<AiPublicModel> {
}
