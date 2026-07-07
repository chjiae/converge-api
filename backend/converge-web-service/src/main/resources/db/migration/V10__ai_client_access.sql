-- 阶段 06：AI 下游 Client API Key 与访问组授权。
-- 本迁移不保存 raw key、Authorization、x-api-key、明文 secret、Cookie、密码或任何可直接调用的秘密。

CREATE TABLE ai_access_group (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    admin_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_ai_access_group_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_ai_access_group_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_ai_access_group_status CHECK (admin_status IN ('ENABLED', 'DISABLED'))
);

CREATE INDEX idx_ai_access_group_tenant_status ON ai_access_group(tenant_id, admin_status);
CREATE INDEX idx_ai_access_group_tenant_created_at ON ai_access_group(tenant_id, created_at);

CREATE TABLE ai_access_group_model_grant (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    access_group_id BIGINT NOT NULL,
    public_model_id BIGINT NOT NULL,
    canonical_operation VARCHAR(64) NOT NULL,
    admin_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_ai_access_grant_tenant_group_model_operation
        UNIQUE (tenant_id, access_group_id, public_model_id, canonical_operation),
    CONSTRAINT uk_ai_access_grant_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ai_access_grant_group_tenant FOREIGN KEY (tenant_id, access_group_id)
        REFERENCES ai_access_group(tenant_id, id),
    CONSTRAINT fk_ai_access_grant_public_model_tenant FOREIGN KEY (tenant_id, public_model_id)
        REFERENCES ai_public_model(tenant_id, id),
    CONSTRAINT ck_ai_access_grant_status CHECK (admin_status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_ai_access_grant_operation CHECK (canonical_operation IN (
        'CHAT_COMPLETIONS', 'RESPONSES', 'MESSAGES', 'EMBEDDINGS',
        'RERANK', 'IMAGE_GENERATION', 'AUDIO_SPEECH', 'AUDIO_TRANSCRIPTION'
    ))
);

CREATE INDEX idx_ai_access_grant_tenant_group ON ai_access_group_model_grant(tenant_id, access_group_id);
CREATE INDEX idx_ai_access_grant_tenant_model ON ai_access_group_model_grant(tenant_id, public_model_id, canonical_operation);

CREATE TABLE ai_client_api_key (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    key_id VARCHAR(64) NOT NULL,
    admin_status VARCHAR(32) NOT NULL,
    secret_hash_algorithm VARCHAR(32) NOT NULL,
    secret_verifier_salt BYTEA NOT NULL,
    secret_verifier_hash BYTEA NOT NULL,
    masked_preview VARCHAR(96) NOT NULL,
    key_version INTEGER NOT NULL,
    expires_at TIMESTAMP,
    rotated_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_ai_client_key_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_ai_client_key_key_id UNIQUE (key_id),
    CONSTRAINT uk_ai_client_key_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_ai_client_key_status CHECK (admin_status IN ('ENABLED', 'DISABLED', 'REVOKED')),
    CONSTRAINT ck_ai_client_key_algorithm CHECK (secret_hash_algorithm = 'SHA-256'),
    CONSTRAINT ck_ai_client_key_version CHECK (key_version >= 1),
    CONSTRAINT ck_ai_client_key_salt_length CHECK (octet_length(secret_verifier_salt) = 16),
    CONSTRAINT ck_ai_client_key_hash_length CHECK (octet_length(secret_verifier_hash) = 32)
);

CREATE INDEX idx_ai_client_key_tenant_status ON ai_client_api_key(tenant_id, admin_status);
CREATE INDEX idx_ai_client_key_tenant_created_at ON ai_client_api_key(tenant_id, created_at);
CREATE INDEX idx_ai_client_key_expires_at ON ai_client_api_key(expires_at);

CREATE TABLE ai_client_api_key_access_group (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    client_api_key_id BIGINT NOT NULL,
    access_group_id BIGINT NOT NULL,
    admin_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_ai_client_key_group_tenant_key_group UNIQUE (tenant_id, client_api_key_id, access_group_id),
    CONSTRAINT uk_ai_client_key_group_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ai_client_key_group_key_tenant FOREIGN KEY (tenant_id, client_api_key_id)
        REFERENCES ai_client_api_key(tenant_id, id),
    CONSTRAINT fk_ai_client_key_group_group_tenant FOREIGN KEY (tenant_id, access_group_id)
        REFERENCES ai_access_group(tenant_id, id),
    CONSTRAINT ck_ai_client_key_group_status CHECK (admin_status IN ('ENABLED', 'DISABLED'))
);

CREATE INDEX idx_ai_client_key_group_tenant_key ON ai_client_api_key_access_group(tenant_id, client_api_key_id);
CREATE INDEX idx_ai_client_key_group_tenant_group ON ai_client_api_key_access_group(tenant_id, access_group_id);
