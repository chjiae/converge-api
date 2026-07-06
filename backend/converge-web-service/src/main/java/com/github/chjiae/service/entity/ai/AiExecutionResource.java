package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.enums.AiResourceStatus;
import com.github.chjiae.common.enums.AiResourceType;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 可执行资源实体，对应 ai_execution_resource 表。
 * 将 AiUpstreamConnection 与 AiCredential 组合为静态绑定资源，
 * 未来可被调度器选择用于上游 API 调用。
 * 资源绑定一经创建不可修改 connectionId、credentialId 或 providerId。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_execution_resource")
public class AiExecutionResource extends BaseEntity {

    /** 所属供应商 ID，显式保存用于数据库与 Service 双重一致性校验 */
    private Long providerId;

    /** 关联的上游连接 ID（阶段 02） */
    private Long upstreamConnectionId;

    /** 关联的凭据 ID（本阶段） */
    private Long credentialId;

    /** 资源类型，本阶段只能为 DIRECT_API */
    private AiResourceType resourceType;

    /** 资源编码，必填且在租户内唯一 */
    private String code;

    /** 资源展示名称，必填 */
    private String displayName;

    /** 资源描述，可选 */
    private String description;

    /** 管理状态：ENABLED / DISABLED / DRAINING */
    private AiResourceStatus adminStatus;
}
