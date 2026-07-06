-- ============================================================
-- V7__ai_credential_and_execution_resource.sql
-- AI 上游凭据保险库与 Direct API 可执行资源
-- 凭据表保存加密后的 API Key，执行资源表保存连接与凭据的静态绑定。
-- 本迁移不包含任何明文密钥、OAuth Token 或敏感数据。
-- ============================================================

-- ============================================================
-- ai_credential：AI 上游凭据保险库
-- ============================================================
CREATE TABLE ai_credential (
    id                   BIGSERIAL      PRIMARY KEY,
    tenant_id            BIGINT         NOT NULL,
    provider_id          BIGINT         NOT NULL,
    code                 VARCHAR(64)    NOT NULL,
    display_name         VARCHAR(128)   NOT NULL,
    description          VARCHAR(512),
    credential_type      VARCHAR(32)    NOT NULL DEFAULT 'API_KEY',
    admin_status         VARCHAR(32)    NOT NULL DEFAULT 'ENABLED',
    secret_reference     VARCHAR(64)    NOT NULL,
    encryption_key_id    VARCHAR(64)    NOT NULL,
    encryption_algorithm VARCHAR(32)    NOT NULL DEFAULT 'AES-256-GCM',
    encrypted_secret     BYTEA          NOT NULL,
    nonce                BYTEA          NOT NULL,
    secret_fingerprint   VARCHAR(128)   NOT NULL,
    masked_preview       VARCHAR(64)    NOT NULL,
    secret_version       INT            NOT NULL DEFAULT 1,
    rotated_at           TIMESTAMP,
    created_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 同一租户/同一供应商/同一凭据类型/同一指纹只允许一条记录（防止重复 API Key）
    CONSTRAINT uk_ai_credential_tenant_provider_type_fingerprint
        UNIQUE (tenant_id, provider_id, credential_type, secret_fingerprint),
    -- 复合唯一约束：支持执行资源表的复合外键引用
    CONSTRAINT uk_ai_credential_tenant_id UNIQUE (tenant_id, id),
    -- 复合外键：确保 provider_id 属于同一租户
    CONSTRAINT fk_ai_credential_provider_tenant
        FOREIGN KEY (tenant_id, provider_id)
        REFERENCES ai_provider (tenant_id, id)
);

COMMENT ON TABLE ai_credential IS 'AI 上游凭据保险库（加密存储 API Key）';
COMMENT ON COLUMN ai_credential.id IS '主键';
COMMENT ON COLUMN ai_credential.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN ai_credential.provider_id IS '所属 AI 供应商 ID';
COMMENT ON COLUMN ai_credential.code IS '凭据编码（租户/供应商内唯一）';
COMMENT ON COLUMN ai_credential.display_name IS '凭据展示名称';
COMMENT ON COLUMN ai_credential.description IS '凭据描述';
COMMENT ON COLUMN ai_credential.credential_type IS '凭据类型：本阶段仅 API_KEY';
COMMENT ON COLUMN ai_credential.admin_status IS '管理状态：ENABLED / DISABLED';
COMMENT ON COLUMN ai_credential.secret_reference IS '服务端生成的 UUID 引用，用于 AAD 绑定';
COMMENT ON COLUMN ai_credential.encryption_key_id IS '加密主密钥版本标识';
COMMENT ON COLUMN ai_credential.encryption_algorithm IS '加密算法：固定 AES-256-GCM';
COMMENT ON COLUMN ai_credential.encrypted_secret IS 'AES-256-GCM 加密后的密文（含 GCM Tag）';
COMMENT ON COLUMN ai_credential.nonce IS 'AES-GCM 随机 12 字节 Nonce';
COMMENT ON COLUMN ai_credential.secret_fingerprint IS 'HMAC 指纹（用于同租户/同供应商去重）';
COMMENT ON COLUMN ai_credential.masked_preview IS '掩码预览（如 sk-...abcd）';
COMMENT ON COLUMN ai_credential.secret_version IS '凭据版本号（轮换时递增）';
COMMENT ON COLUMN ai_credential.rotated_at IS '最近一次轮换时间';
COMMENT ON COLUMN ai_credential.created_at IS '创建时间';
COMMENT ON COLUMN ai_credential.updated_at IS '更新时间';

CREATE INDEX idx_ai_credential_tenant_provider ON ai_credential (tenant_id, provider_id);
CREATE INDEX idx_ai_credential_tenant_status ON ai_credential (tenant_id, admin_status);
CREATE INDEX idx_ai_credential_tenant_created_at ON ai_credential (tenant_id, created_at DESC);

-- ============================================================
-- ai_execution_resource：Direct API 可执行资源
-- ============================================================

-- V6 遗漏了 ai_upstream_connection 的 (tenant_id, id) 复合唯一约束，
-- 本迁移补充添加，以支持执行资源表的复合外键引用
ALTER TABLE ai_upstream_connection
    ADD CONSTRAINT uk_ai_connection_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE ai_execution_resource (
    id                     BIGSERIAL      PRIMARY KEY,
    tenant_id              BIGINT         NOT NULL,
    provider_id            BIGINT         NOT NULL,
    upstream_connection_id BIGINT         NOT NULL,
    credential_id          BIGINT         NOT NULL,
    resource_type          VARCHAR(32)    NOT NULL DEFAULT 'DIRECT_API',
    code                   VARCHAR(64)    NOT NULL,
    display_name           VARCHAR(128)   NOT NULL,
    description            VARCHAR(512),
    admin_status           VARCHAR(32)    NOT NULL DEFAULT 'ENABLED',
    created_at             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 同一租户/同一连接/同一凭据不允许重复创建资源
    CONSTRAINT uk_ai_resource_tenant_connection_credential
        UNIQUE (tenant_id, upstream_connection_id, credential_id),
    -- 复合外键：确保 connection 属于同一租户
    CONSTRAINT fk_ai_resource_connection_tenant
        FOREIGN KEY (tenant_id, upstream_connection_id)
        REFERENCES ai_upstream_connection (tenant_id, id),
    -- 复合外键：确保 credential 属于同一租户
    CONSTRAINT fk_ai_resource_credential_tenant
        FOREIGN KEY (tenant_id, credential_id)
        REFERENCES ai_credential (tenant_id, id)
);

COMMENT ON TABLE ai_execution_resource IS 'AI 可执行资源（连接 + 凭据的静态绑定）';
COMMENT ON COLUMN ai_execution_resource.id IS '主键';
COMMENT ON COLUMN ai_execution_resource.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN ai_execution_resource.provider_id IS '所属 AI 供应商 ID（冗余保存，用于 Service 层一致性校验）';
COMMENT ON COLUMN ai_execution_resource.upstream_connection_id IS '关联的上游连接 ID';
COMMENT ON COLUMN ai_execution_resource.credential_id IS '关联的凭据 ID';
COMMENT ON COLUMN ai_execution_resource.resource_type IS '资源类型：本阶段仅 DIRECT_API';
COMMENT ON COLUMN ai_execution_resource.code IS '资源编码（租户内唯一）';
COMMENT ON COLUMN ai_execution_resource.display_name IS '资源展示名称';
COMMENT ON COLUMN ai_execution_resource.description IS '资源描述';
COMMENT ON COLUMN ai_execution_resource.admin_status IS '管理状态：ENABLED / DISABLED / DRAINING';
COMMENT ON COLUMN ai_execution_resource.created_at IS '创建时间';
COMMENT ON COLUMN ai_execution_resource.updated_at IS '更新时间';

CREATE INDEX idx_ai_resource_tenant_provider ON ai_execution_resource (tenant_id, provider_id);
CREATE INDEX idx_ai_resource_tenant_status ON ai_execution_resource (tenant_id, admin_status);
CREATE INDEX idx_ai_resource_tenant_created_at ON ai_execution_resource (tenant_id, created_at DESC);
