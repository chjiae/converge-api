package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 下游访问组实体，对应 ai_access_group 表。
 * 访问组只表达 Client API Key 的模型授权集合，不与 ResourcePool 混用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_access_group")
public class AiAccessGroup extends BaseEntity {

    /** 访问组编码，必填且在租户内唯一 */
    private String code;

    /** 访问组展示名称，必填 */
    private String displayName;

    /** 访问组描述，可选 */
    private String description;

    /** 管理状态：ENABLED / DISABLED */
    private AiCatalogStatus adminStatus;
}
