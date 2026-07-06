package com.github.chjiae.service.service.ai;

import com.github.chjiae.service.entity.ai.AiGatewaySnapshotOutbox;
import com.github.chjiae.service.mapper.ai.AiGatewaySnapshotOutboxMapper;
import com.github.chjiae.service.mapper.ai.AiGatewaySnapshotRevisionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 网关快照变更记录器。
 * 必须在业务写操作同一事务内递增 revision 并追加 outbox。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewaySnapshotChangeRecorder {

    /** 初始 outbox 状态 */
    private static final String STATUS_PENDING = "PENDING";

    /** revision Mapper */
    private final AiGatewaySnapshotRevisionMapper revisionMapper;

    /** outbox Mapper */
    private final AiGatewaySnapshotOutboxMapper outboxMapper;

    /**
     * 记录网关快照变更。
     *
     * @param tenantId 租户 ID
     * @param changeType 变更类型
     * @return 新 revision
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public long recordChange(Long tenantId, String changeType) {
        LocalDateTime now = LocalDateTime.now();
        Long revision = revisionMapper.incrementAndGetRevision(tenantId, now);

        AiGatewaySnapshotOutbox outbox = new AiGatewaySnapshotOutbox();
        outbox.setTenantId(tenantId);
        outbox.setRevision(revision);
        outbox.setChangeType(changeType);
        outbox.setStatus(STATUS_PENDING);
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        outboxMapper.insert(outbox);

        log.info("已记录 AI 网关快照变更，租户 ID: {}，revision: {}，类型: {}", tenantId, revision, changeType);
        return revision;
    }
}
