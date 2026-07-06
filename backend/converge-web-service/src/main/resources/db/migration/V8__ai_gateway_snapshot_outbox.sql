-- ============================================================
-- V8__ai_gateway_snapshot_outbox.sql
-- AI 网关快照 revision 事实表与 transactional outbox。
-- 本迁移不保存 API Key、OAuth Token、Cookie、密码、私钥或任何明文秘密。
-- ============================================================

CREATE TABLE ai_gateway_snapshot_revision (
    tenant_id        BIGINT      PRIMARY KEY,
    current_revision BIGINT      NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ai_snapshot_revision_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenant (id)
);

COMMENT ON TABLE ai_gateway_snapshot_revision IS 'AI 网关租户快照 revision 事实表';
COMMENT ON COLUMN ai_gateway_snapshot_revision.tenant_id IS '租户 ID，主键';
COMMENT ON COLUMN ai_gateway_snapshot_revision.current_revision IS '当前租户快照 revision，单调递增';
COMMENT ON COLUMN ai_gateway_snapshot_revision.updated_at IS '最近更新时间';

CREATE TABLE ai_gateway_snapshot_outbox (
    id                 BIGSERIAL     PRIMARY KEY,
    tenant_id          BIGINT        NOT NULL,
    revision           BIGINT        NOT NULL,
    change_type        VARCHAR(64)   NOT NULL,
    status             VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    attempt_count      INT           NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at          TIMESTAMP,
    lock_owner         VARCHAR(128),
    last_error_summary VARCHAR(512),
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at       TIMESTAMP,

    CONSTRAINT uk_ai_snapshot_outbox_tenant_revision UNIQUE (tenant_id, revision),
    CONSTRAINT fk_ai_snapshot_outbox_revision_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenant (id)
);

COMMENT ON TABLE ai_gateway_snapshot_outbox IS 'AI 网关快照 transactional outbox';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.id IS '主键';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.tenant_id IS '租户 ID';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.revision IS '该变更对应的租户快照 revision';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.change_type IS '变更类型';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.status IS '发布状态：PENDING / PROCESSING / PUBLISHED / FAILED';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.attempt_count IS '投影尝试次数';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.next_attempt_at IS '下一次可重试时间';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.locked_at IS '投影认领时间';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.lock_owner IS '投影认领者';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.last_error_summary IS '安全错误摘要，禁止包含秘密';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.created_at IS '创建时间';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.updated_at IS '更新时间';
COMMENT ON COLUMN ai_gateway_snapshot_outbox.published_at IS '成功发布时间';

CREATE INDEX idx_ai_snapshot_outbox_due
    ON ai_gateway_snapshot_outbox (status, next_attempt_at, id);
CREATE INDEX idx_ai_snapshot_outbox_tenant_revision
    ON ai_gateway_snapshot_outbox (tenant_id, revision);
CREATE INDEX idx_ai_snapshot_outbox_locked
    ON ai_gateway_snapshot_outbox (locked_at, lock_owner);
