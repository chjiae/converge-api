package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 网关租户快照 revision 事实实体。
 * 每个租户一行，currentRevision 单调递增，是 Redis 快照版本的事实来源。
 */
@Data
@TableName("ai_gateway_snapshot_revision")
public class AiGatewaySnapshotRevision {

    /** 租户 ID，主键且不可为空 */
    @TableId(value = "tenant_id", type = IdType.INPUT)
    private Long tenantId;

    /** 当前租户快照 revision，单调递增 */
    private Long currentRevision;

    /** 最近更新时间 */
    private LocalDateTime updatedAt;
}
