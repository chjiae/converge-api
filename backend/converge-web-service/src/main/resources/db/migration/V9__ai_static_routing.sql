-- 阶段 05：AI 静态路由拓扑。本迁移不保存 API Key、密文、nonce、HMAC、Cookie、密码或任何明文秘密。

-- 阶段 05 需要用复合外键表达租户一致性，补齐历史表的 (tenant_id, id) 唯一约束。
ALTER TABLE ai_public_model
    ADD CONSTRAINT uk_ai_public_model_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE ai_execution_resource
    ADD CONSTRAINT uk_ai_resource_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE ai_resource_pool (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    admin_status VARCHAR(32) NOT NULL,
    selection_policy VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_ai_resource_pool_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_ai_resource_pool_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_ai_resource_pool_status CHECK (admin_status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_ai_resource_pool_policy CHECK (selection_policy = 'PRIORITY_WEIGHTED')
);

CREATE INDEX idx_ai_resource_pool_tenant_status ON ai_resource_pool(tenant_id, admin_status);
CREATE INDEX idx_ai_resource_pool_tenant_created_at ON ai_resource_pool(tenant_id, created_at);

CREATE TABLE ai_resource_pool_member (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    resource_pool_id BIGINT NOT NULL,
    execution_resource_id BIGINT NOT NULL,
    admin_status VARCHAR(32) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    weight INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_ai_pool_member_tenant_pool_resource UNIQUE (tenant_id, resource_pool_id, execution_resource_id),
    CONSTRAINT uk_ai_pool_member_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ai_pool_member_pool_tenant FOREIGN KEY (tenant_id, resource_pool_id)
        REFERENCES ai_resource_pool(tenant_id, id),
    CONSTRAINT fk_ai_pool_member_resource_tenant FOREIGN KEY (tenant_id, execution_resource_id)
        REFERENCES ai_execution_resource(tenant_id, id),
    CONSTRAINT ck_ai_pool_member_status CHECK (admin_status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_ai_pool_member_priority CHECK (priority BETWEEN -100000 AND 100000),
    CONSTRAINT ck_ai_pool_member_weight CHECK (weight BETWEEN 1 AND 100000)
);

CREATE INDEX idx_ai_pool_member_tenant_pool ON ai_resource_pool_member(tenant_id, resource_pool_id);
CREATE INDEX idx_ai_pool_member_tenant_resource ON ai_resource_pool_member(tenant_id, execution_resource_id);

CREATE TABLE ai_resource_model_binding (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    execution_resource_id BIGINT NOT NULL,
    public_model_id BIGINT NOT NULL,
    canonical_operation VARCHAR(64) NOT NULL,
    upstream_model_name VARCHAR(128) NOT NULL,
    admin_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_ai_model_binding_tenant_resource_model_operation
        UNIQUE (tenant_id, execution_resource_id, public_model_id, canonical_operation),
    CONSTRAINT uk_ai_model_binding_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ai_model_binding_resource_tenant FOREIGN KEY (tenant_id, execution_resource_id)
        REFERENCES ai_execution_resource(tenant_id, id),
    CONSTRAINT fk_ai_model_binding_public_model_tenant FOREIGN KEY (tenant_id, public_model_id)
        REFERENCES ai_public_model(tenant_id, id),
    CONSTRAINT ck_ai_model_binding_status CHECK (admin_status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_ai_model_binding_operation CHECK (canonical_operation IN (
        'CHAT_COMPLETIONS', 'RESPONSES', 'MESSAGES', 'EMBEDDINGS',
        'RERANK', 'IMAGE_GENERATION', 'AUDIO_SPEECH', 'AUDIO_TRANSCRIPTION'
    )),
    CONSTRAINT ck_ai_model_binding_upstream_not_wildcard CHECK (
        upstream_model_name <> '*'
        AND upstream_model_name NOT LIKE '%*%'
        AND upstream_model_name NOT LIKE '^%'
        AND upstream_model_name NOT LIKE '%$'
    )
);

CREATE INDEX idx_ai_model_binding_tenant_model ON ai_resource_model_binding(tenant_id, public_model_id, canonical_operation);
CREATE INDEX idx_ai_model_binding_tenant_resource ON ai_resource_model_binding(tenant_id, execution_resource_id);

CREATE TABLE ai_route_policy (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    public_model_id BIGINT NOT NULL,
    canonical_operation VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    admin_status VARCHAR(32) NOT NULL,
    selection_policy VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_ai_route_policy_tenant_model_operation UNIQUE (tenant_id, public_model_id, canonical_operation),
    CONSTRAINT uk_ai_route_policy_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ai_route_policy_public_model_tenant FOREIGN KEY (tenant_id, public_model_id)
        REFERENCES ai_public_model(tenant_id, id),
    CONSTRAINT ck_ai_route_policy_status CHECK (admin_status IN ('DRAFT', 'ENABLED', 'DISABLED')),
    CONSTRAINT ck_ai_route_policy_policy CHECK (selection_policy = 'PRIORITY_WEIGHTED'),
    CONSTRAINT ck_ai_route_policy_operation CHECK (canonical_operation IN (
        'CHAT_COMPLETIONS', 'RESPONSES', 'MESSAGES', 'EMBEDDINGS',
        'RERANK', 'IMAGE_GENERATION', 'AUDIO_SPEECH', 'AUDIO_TRANSCRIPTION'
    ))
);

CREATE INDEX idx_ai_route_policy_tenant_status ON ai_route_policy(tenant_id, admin_status);
CREATE INDEX idx_ai_route_policy_tenant_created_at ON ai_route_policy(tenant_id, created_at);

CREATE TABLE ai_route_target (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    route_policy_id BIGINT NOT NULL,
    resource_pool_id BIGINT NOT NULL,
    admin_status VARCHAR(32) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    weight INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_ai_route_target_tenant_policy_pool UNIQUE (tenant_id, route_policy_id, resource_pool_id),
    CONSTRAINT uk_ai_route_target_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ai_route_target_policy_tenant FOREIGN KEY (tenant_id, route_policy_id)
        REFERENCES ai_route_policy(tenant_id, id),
    CONSTRAINT fk_ai_route_target_pool_tenant FOREIGN KEY (tenant_id, resource_pool_id)
        REFERENCES ai_resource_pool(tenant_id, id),
    CONSTRAINT ck_ai_route_target_status CHECK (admin_status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_ai_route_target_priority CHECK (priority BETWEEN -100000 AND 100000),
    CONSTRAINT ck_ai_route_target_weight CHECK (weight BETWEEN 1 AND 100000)
);

CREATE INDEX idx_ai_route_target_tenant_policy ON ai_route_target(tenant_id, route_policy_id);
CREATE INDEX idx_ai_route_target_tenant_pool ON ai_route_target(tenant_id, resource_pool_id);
