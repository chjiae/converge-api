package com.github.chjiae.service.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.ai.AiCredential;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 凭据数据访问层
 */
@Mapper
public interface AiCredentialMapper extends BaseMapper<AiCredential> {
}
