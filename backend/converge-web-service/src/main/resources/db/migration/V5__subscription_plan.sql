-- ============================================================
-- V5__subscription_plan.sql
-- 订阅套餐配置表，支持超管配置价格、权益和当前折扣价
-- ============================================================

CREATE TABLE subscription_plan (
    id                BIGSERIAL      PRIMARY KEY,
    code              VARCHAR(64)    NOT NULL UNIQUE,
    name              VARCHAR(64)    NOT NULL,
    plan_type         VARCHAR(32)    NOT NULL,
    duration_months   INTEGER        NOT NULL,
    original_price    DECIMAL(12, 2) NOT NULL,
    discount_name     VARCHAR(64),
    discount_price    DECIMAL(12, 2),
    discount_start_at TIMESTAMP,
    discount_end_at   TIMESTAMP,
    benefits          TEXT,
    enabled           BOOLEAN        NOT NULL DEFAULT TRUE,
    recommended       BOOLEAN        NOT NULL DEFAULT FALSE,
    sort_order        INTEGER        NOT NULL DEFAULT 0,
    created_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE subscription_plan IS '订阅套餐配置';
COMMENT ON COLUMN subscription_plan.code IS '套餐编码，系统内唯一';
COMMENT ON COLUMN subscription_plan.name IS '套餐展示名称';
COMMENT ON COLUMN subscription_plan.plan_type IS '套餐类型：MONTHLY / QUARTERLY / YEARLY / CUSTOM';
COMMENT ON COLUMN subscription_plan.duration_months IS '套餐有效月数';
COMMENT ON COLUMN subscription_plan.original_price IS '套餐原价';
COMMENT ON COLUMN subscription_plan.discount_name IS '当前折扣活动名称';
COMMENT ON COLUMN subscription_plan.discount_price IS '当前折扣成交价';
COMMENT ON COLUMN subscription_plan.discount_start_at IS '折扣开始时间';
COMMENT ON COLUMN subscription_plan.discount_end_at IS '折扣结束时间';
COMMENT ON COLUMN subscription_plan.benefits IS '权益说明，每行一条';
COMMENT ON COLUMN subscription_plan.enabled IS '是否启用';
COMMENT ON COLUMN subscription_plan.recommended IS '是否推荐展示';
COMMENT ON COLUMN subscription_plan.sort_order IS '展示排序，越小越靠前';

INSERT INTO subscription_plan (
    code, name, plan_type, duration_months, original_price,
    benefits, enabled, recommended, sort_order
) VALUES
('monthly', '月付套餐', 'MONTHLY', 1, 99.00,
 '适合短期项目和小团队试用
完整 API 聚合能力
标准调用额度
社区支持', TRUE, FALSE, 1),
('quarterly', '季付套餐', 'QUARTERLY', 3, 267.00,
 '比月付节省 10%
适合稳定业务迭代
完整 API 聚合能力
优先工单响应', TRUE, TRUE, 2),
('yearly', '年付套餐', 'YEARLY', 12, 948.00,
 '比月付节省 20%
适合长期生产环境
完整 API 聚合能力
优先工单响应
年度账单更省心', TRUE, FALSE, 3);
