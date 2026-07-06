-- ============================================================
-- V6__ai_control_plane_catalog.sql
-- AI 控制面目录表：供应商、上游连接元数据、公开模型目录
-- 本迁移只保存非敏感元数据，不包含 API Key、OAuth Token、Cookie、密码或私钥。
-- ============================================================

CREATE TABLE ai_provider (
    id            BIGSERIAL     PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL,
    code          VARCHAR(64)   NOT NULL,
    display_name  VARCHAR(128)  NOT NULL,
    provider_kind VARCHAR(32)   NOT NULL,
    status        VARCHAR(32)   NOT NULL DEFAULT 'ENABLED',
    description   VARCHAR(512),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_provider_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_ai_provider_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ai_provider_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

COMMENT ON TABLE ai_provider IS 'AI 供应商目录';
COMMENT ON COLUMN ai_provider.id IS '主键';
COMMENT ON COLUMN ai_provider.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN ai_provider.code IS '租户内供应商编码';
COMMENT ON COLUMN ai_provider.display_name IS '供应商展示名称';
COMMENT ON COLUMN ai_provider.provider_kind IS '供应商类型：OPENAI / ANTHROPIC / GOOGLE / XAI / CUSTOM';
COMMENT ON COLUMN ai_provider.status IS '目录状态：ENABLED / DISABLED';
COMMENT ON COLUMN ai_provider.description IS '供应商描述';
COMMENT ON COLUMN ai_provider.created_at IS '创建时间';
COMMENT ON COLUMN ai_provider.updated_at IS '更新时间';

CREATE INDEX idx_ai_provider_tenant_status ON ai_provider (tenant_id, status);
CREATE INDEX idx_ai_provider_tenant_created_at ON ai_provider (tenant_id, created_at DESC);

CREATE TABLE ai_upstream_connection (
    id            BIGSERIAL     PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL,
    provider_id   BIGINT        NOT NULL,
    code          VARCHAR(64)   NOT NULL,
    display_name  VARCHAR(128)  NOT NULL,
    protocol_type VARCHAR(64)   NOT NULL,
    base_url      VARCHAR(512)  NOT NULL,
    status        VARCHAR(32)   NOT NULL DEFAULT 'ENABLED',
    description   VARCHAR(512),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_connection_tenant_provider_code UNIQUE (tenant_id, provider_id, code),
    CONSTRAINT fk_ai_connection_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_ai_connection_provider_tenant FOREIGN KEY (tenant_id, provider_id)
        REFERENCES ai_provider (tenant_id, id)
);

COMMENT ON TABLE ai_upstream_connection IS 'AI 上游连接元数据';
COMMENT ON COLUMN ai_upstream_connection.id IS '主键';
COMMENT ON COLUMN ai_upstream_connection.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN ai_upstream_connection.provider_id IS '所属 AI 供应商 ID';
COMMENT ON COLUMN ai_upstream_connection.code IS '供应商内连接编码';
COMMENT ON COLUMN ai_upstream_connection.display_name IS '连接展示名称';
COMMENT ON COLUMN ai_upstream_connection.protocol_type IS '协议类型：OPENAI_COMPATIBLE / ANTHROPIC_MESSAGES / GEMINI_GENERATIVE_AI';
COMMENT ON COLUMN ai_upstream_connection.base_url IS '上游 Base URL，仅保存非敏感地址';
COMMENT ON COLUMN ai_upstream_connection.status IS '目录状态：ENABLED / DISABLED';
COMMENT ON COLUMN ai_upstream_connection.description IS '连接描述';
COMMENT ON COLUMN ai_upstream_connection.created_at IS '创建时间';
COMMENT ON COLUMN ai_upstream_connection.updated_at IS '更新时间';

CREATE INDEX idx_ai_connection_tenant_provider ON ai_upstream_connection (tenant_id, provider_id);
CREATE INDEX idx_ai_connection_tenant_status ON ai_upstream_connection (tenant_id, status);
CREATE INDEX idx_ai_connection_tenant_created_at ON ai_upstream_connection (tenant_id, created_at DESC);

CREATE TABLE ai_public_model (
    id            BIGSERIAL     PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL,
    code          VARCHAR(64)   NOT NULL,
    display_name  VARCHAR(128)  NOT NULL,
    model_family  VARCHAR(64)   NOT NULL,
    status        VARCHAR(32)   NOT NULL DEFAULT 'ENABLED',
    description   VARCHAR(512),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_public_model_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_ai_public_model_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

COMMENT ON TABLE ai_public_model IS 'AI 公开模型目录';
COMMENT ON COLUMN ai_public_model.id IS '主键';
COMMENT ON COLUMN ai_public_model.tenant_id IS '所属租户 ID';
COMMENT ON COLUMN ai_public_model.code IS '下游公开模型别名，租户内唯一';
COMMENT ON COLUMN ai_public_model.display_name IS '公开模型展示名称';
COMMENT ON COLUMN ai_public_model.model_family IS '模型族或产品线';
COMMENT ON COLUMN ai_public_model.status IS '目录状态：ENABLED / DISABLED';
COMMENT ON COLUMN ai_public_model.description IS '公开模型描述';
COMMENT ON COLUMN ai_public_model.created_at IS '创建时间';
COMMENT ON COLUMN ai_public_model.updated_at IS '更新时间';

CREATE INDEX idx_ai_public_model_tenant_status ON ai_public_model (tenant_id, status);
CREATE INDEX idx_ai_public_model_tenant_family ON ai_public_model (tenant_id, model_family);
CREATE INDEX idx_ai_public_model_tenant_created_at ON ai_public_model (tenant_id, created_at DESC);
