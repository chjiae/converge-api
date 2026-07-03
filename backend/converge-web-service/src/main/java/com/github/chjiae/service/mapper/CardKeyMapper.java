package com.github.chjiae.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.CardKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * 卡密数据访问层
 */
@Mapper
public interface CardKeyMapper extends BaseMapper<CardKey> {
}
