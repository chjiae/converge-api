package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiCatalogStatus;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI Client API Key 与访问组绑定实体，对应 ai_client_api_key_access_group 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_client_api_key_access_group")
public class AiClientApiKeyAccessGroup extends BaseEntity {

    /** Client API Key ID，必填且必须属于同一租户 */
    private Long clientApiKeyId;

    /** 访问组 ID，必填且必须属于同一租户 */
    private Long accessGroupId;

    /** 管理状态：ENABLED / DISABLED */
    private AiCatalogStatus adminStatus;
}
