package com.github.chjiae.service.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.chjiae.common.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 网关快照 transactional outbox 实体。
 * 只记录非敏感变更事实、状态、重试和安全错误摘要，不保存任何密钥或密文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_gateway_snapshot_outbox")
public class AiGatewaySnapshotOutbox extends BaseEntity {

    /** 该变更对应的租户快照 revision */
    private Long revision;

    /** 变更类型 */
    private String changeType;

    /** 发布状态：PENDING / PROCESSING / PUBLISHED / FAILED */
    private String status;

    /** 投影尝试次数 */
    private Integer attemptCount;

    /** 下一次可重试时间 */
    private LocalDateTime nextAttemptAt;

    /** 投影认领时间 */
    private LocalDateTime lockedAt;

    /** 投影认领者 */
    private String lockOwner;

    /** 安全错误摘要，禁止包含秘密 */
    private String lastErrorSummary;

    /** 成功发布时间 */
    private LocalDateTime publishedAt;
}
