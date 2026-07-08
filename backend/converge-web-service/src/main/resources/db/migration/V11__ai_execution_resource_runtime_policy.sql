-- 阶段 08：AI 执行资源运行时治理策略。
-- 本迁移只保存并发、失败阈值和冷却时间等安全治理参数，不保存 API Key、密文、nonce、HMAC、Cookie 或密码。

CREATE TABLE ai_execution_resource_runtime_policy (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    execution_resource_id BIGINT NOT NULL,
    max_concurrent_requests INTEGER NOT NULL DEFAULT 0,
    consecutive_failure_threshold INTEGER NOT NULL DEFAULT 3,
    failure_reset_after_ms BIGINT NOT NULL DEFAULT 300000,
    failure_cooldown_ms BIGINT NOT NULL DEFAULT 30000,
    rate_limit_cooldown_ms BIGINT NOT NULL DEFAULT 60000,
    policy_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_runtime_policy_tenant_resource UNIQUE (tenant_id, execution_resource_id),
    CONSTRAINT uk_ai_runtime_policy_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ai_runtime_policy_resource_tenant FOREIGN KEY (tenant_id, execution_resource_id)
        REFERENCES ai_execution_resource(tenant_id, id),
    CONSTRAINT ck_ai_runtime_policy_max_concurrent CHECK (max_concurrent_requests >= 0),
    CONSTRAINT ck_ai_runtime_policy_failure_threshold CHECK (consecutive_failure_threshold > 0),
    CONSTRAINT ck_ai_runtime_policy_failure_reset CHECK (failure_reset_after_ms > 0),
    CONSTRAINT ck_ai_runtime_policy_failure_cooldown CHECK (failure_cooldown_ms > 0),
    CONSTRAINT ck_ai_runtime_policy_rate_limit_cooldown CHECK (rate_limit_cooldown_ms > 0),
    CONSTRAINT ck_ai_runtime_policy_version CHECK (policy_version >= 1)
);

COMMENT ON TABLE ai_execution_resource_runtime_policy IS 'AI 执行资源运行时治理策略';
COMMENT ON COLUMN ai_execution_resource_runtime_policy.id IS '主键';
COMMENT ON COLUMN ai_execution_resource_runtime_policy.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN ai_execution_resource_runtime_policy.execution_resource_id IS '关联执行资源 ID';
COMMENT ON COLUMN ai_execution_resource_runtime_policy.max_concurrent_requests IS '最大并发请求数，0 表示不限制';
COMMENT ON COLUMN ai_execution_resource_runtime_policy.consecutive_failure_threshold IS '连续失败打开熔断阈值';
COMMENT ON COLUMN ai_execution_resource_runtime_policy.failure_reset_after_ms IS '连续失败计数重置窗口，单位毫秒';
COMMENT ON COLUMN ai_execution_resource_runtime_policy.failure_cooldown_ms IS '普通上游失败熔断冷却时间，单位毫秒';
COMMENT ON COLUMN ai_execution_resource_runtime_policy.rate_limit_cooldown_ms IS '上游 429 冷却时间，单位毫秒';
COMMENT ON COLUMN ai_execution_resource_runtime_policy.policy_version IS '策略版本，更新时单调递增';
COMMENT ON COLUMN ai_execution_resource_runtime_policy.created_at IS '创建时间';
COMMENT ON COLUMN ai_execution_resource_runtime_policy.updated_at IS '更新时间';

CREATE INDEX idx_ai_runtime_policy_tenant_resource
    ON ai_execution_resource_runtime_policy(tenant_id, execution_resource_id);

INSERT INTO ai_execution_resource_runtime_policy (
    tenant_id,
    execution_resource_id,
    max_concurrent_requests,
    consecutive_failure_threshold,
    failure_reset_after_ms,
    failure_cooldown_ms,
    rate_limit_cooldown_ms,
    policy_version,
    created_at,
    updated_at
)
SELECT tenant_id,
       id,
       0,
       3,
       300000,
       30000,
       60000,
       1,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM ai_execution_resource;
