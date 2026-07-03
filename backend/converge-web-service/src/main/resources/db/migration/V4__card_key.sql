-- 卡密表
CREATE TABLE card_key (
    id             BIGSERIAL      PRIMARY KEY,
    code           VARCHAR(32)    NOT NULL,
    plan_type      VARCHAR(32)    NOT NULL,
    duration_days  INTEGER        NOT NULL,
    amount         DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    status         VARCHAR(32)    NOT NULL DEFAULT 'UNUSED',
    generated_by   BIGINT,
    redeemed_by    BIGINT,
    redeemed_at    TIMESTAMP,
    tenant_id      BIGINT,
    expired_at     TIMESTAMP,
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_card_key_code UNIQUE (code)
);

COMMENT ON TABLE card_key IS '卡密表';
COMMENT ON COLUMN card_key.id IS '主键 ID';
COMMENT ON COLUMN card_key.code IS '卡密编码（唯一，16 位大写字母+数字）';
COMMENT ON COLUMN card_key.plan_type IS '套餐类型：TRIAL / MONTHLY / QUARTERLY / YEARLY / CUSTOM';
COMMENT ON COLUMN card_key.duration_days IS '有效天数';
COMMENT ON COLUMN card_key.amount IS '面值金额';
COMMENT ON COLUMN card_key.status IS '卡密状态：UNUSED / REDEEMED / EXPIRED';
COMMENT ON COLUMN card_key.generated_by IS '生成人用户 ID';
COMMENT ON COLUMN card_key.redeemed_by IS '兑换人用户 ID';
COMMENT ON COLUMN card_key.redeemed_at IS '兑换时间';
COMMENT ON COLUMN card_key.tenant_id IS '兑换后所属租户 ID';
COMMENT ON COLUMN card_key.expired_at IS '卡密过期时间';
COMMENT ON COLUMN card_key.created_at IS '创建时间';

CREATE INDEX idx_card_key_status ON card_key (status);
CREATE INDEX idx_card_key_tenant_id ON card_key (tenant_id);
