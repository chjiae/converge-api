-- ============================================================
-- V1__init_schema.sql
-- Converge API 多租户体系 - 数据库初始化脚本（PostgreSQL 版本）
-- 包含所有核心表结构、索引和初始数据
-- ============================================================

-- -----------------------------------------------------------
-- 1. 租户表 (tenant)
-- -----------------------------------------------------------
CREATE TABLE tenant (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    trial_used  BOOLEAN      NOT NULL DEFAULT FALSE,
    expired_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    CONSTRAINT uk_tenant_code UNIQUE (code)
);

COMMENT ON TABLE tenant IS '租户';
COMMENT ON COLUMN tenant.id IS '主键';
COMMENT ON COLUMN tenant.code IS '租户编码（唯一，用于 URL/标识）';
COMMENT ON COLUMN tenant.name IS '租户名称';
COMMENT ON COLUMN tenant.description IS '租户描述';
COMMENT ON COLUMN tenant.status IS '状态：PENDING / TRIAL / ACTIVE / DISABLED / EXPIRED / DELETED';
COMMENT ON COLUMN tenant.trial_used IS '是否已使用过试用（每个租户只能试用一次）';
COMMENT ON COLUMN tenant.expired_at IS '到期时间（NULL 表示永不过期）';
COMMENT ON COLUMN tenant.created_at IS '创建时间';
COMMENT ON COLUMN tenant.updated_at IS '更新时间';
COMMENT ON COLUMN tenant.created_by IS '创建人（超管 ID 或自注册申请人 ID）';

-- -----------------------------------------------------------
-- 2. 租户注册申请表 (tenant_application)
-- -----------------------------------------------------------
CREATE TABLE tenant_application (
    id               BIGSERIAL     PRIMARY KEY,
    company_name     VARCHAR(256)  NOT NULL,
    contact_name     VARCHAR(64)   NOT NULL,
    contact_email    VARCHAR(128)  NOT NULL,
    contact_phone    VARCHAR(32),
    description      VARCHAR(1024),
    application_type VARCHAR(32)   NOT NULL,
    status           VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    reject_reason    VARCHAR(512),
    reviewed_by      BIGINT,
    reviewed_at      TIMESTAMP,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE tenant_application IS '租户注册申请';
COMMENT ON COLUMN tenant_application.id IS '主键';
COMMENT ON COLUMN tenant_application.company_name IS '公司/组织名称';
COMMENT ON COLUMN tenant_application.contact_name IS '联系人姓名';
COMMENT ON COLUMN tenant_application.contact_email IS '联系人邮箱';
COMMENT ON COLUMN tenant_application.contact_phone IS '联系人电话';
COMMENT ON COLUMN tenant_application.description IS '用途描述';
COMMENT ON COLUMN tenant_application.application_type IS '申请类型：REGISTER（注册充值）/ TRIAL（申请试用）';
COMMENT ON COLUMN tenant_application.status IS '状态：PENDING / APPROVED / REJECTED';
COMMENT ON COLUMN tenant_application.reject_reason IS '拒绝原因';
COMMENT ON COLUMN tenant_application.reviewed_by IS '审核人（超管 ID）';
COMMENT ON COLUMN tenant_application.reviewed_at IS '审核时间';
COMMENT ON COLUMN tenant_application.created_at IS '申请时间';
COMMENT ON COLUMN tenant_application.updated_at IS '更新时间';

CREATE INDEX idx_application_status ON tenant_application (status);
CREATE INDEX idx_application_contact_email ON tenant_application (contact_email);

-- -----------------------------------------------------------
-- 3. 订阅/订单表 (subscription)
-- -----------------------------------------------------------
CREATE TABLE subscription (
    id             BIGSERIAL      PRIMARY KEY,
    tenant_id      BIGINT         NOT NULL,
    plan_type      VARCHAR(32)    NOT NULL,
    amount         DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    start_date     DATE           NOT NULL,
    end_date       DATE           NOT NULL,
    status         VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(32),
    payment_ref    VARCHAR(128),
    remark         VARCHAR(512),
    created_by     BIGINT,
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE subscription IS '订阅/订单';
COMMENT ON COLUMN subscription.id IS '主键';
COMMENT ON COLUMN subscription.tenant_id IS '租户 ID';
COMMENT ON COLUMN subscription.plan_type IS '套餐类型：TRIAL / MONTHLY / QUARTERLY / YEARLY / CUSTOM';
COMMENT ON COLUMN subscription.amount IS '金额（试用为 0）';
COMMENT ON COLUMN subscription.start_date IS '生效日期';
COMMENT ON COLUMN subscription.end_date IS '到期日期';
COMMENT ON COLUMN subscription.status IS '状态：PENDING / PAID / ACTIVE / EXPIRED / CANCELLED';
COMMENT ON COLUMN subscription.payment_method IS '支付方式：ALIPAY / WECHAT / CARD_KEY / OFFLINE';
COMMENT ON COLUMN subscription.payment_ref IS '支付凭证号';
COMMENT ON COLUMN subscription.remark IS '备注';
COMMENT ON COLUMN subscription.created_by IS '创建人（租户管理员或超管）';
COMMENT ON COLUMN subscription.created_at IS '创建时间';
COMMENT ON COLUMN subscription.updated_at IS '更新时间';

CREATE INDEX idx_subscription_tenant_id ON subscription (tenant_id);
CREATE INDEX idx_subscription_status ON subscription (status);

-- -----------------------------------------------------------
-- 4. 用户表 ("user" — PostgreSQL 保留字，必须加双引号)
-- -----------------------------------------------------------
CREATE TABLE "user" (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT,
    username      VARCHAR(64)  NOT NULL,
    email         VARCHAR(128) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    phone         VARCHAR(32),
    status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    user_type     VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_email_tenant UNIQUE (email, tenant_id),
    CONSTRAINT uk_user_username_tenant UNIQUE (username, tenant_id)
);

COMMENT ON TABLE "user" IS '用户';
COMMENT ON COLUMN "user".id IS '主键';
COMMENT ON COLUMN "user".tenant_id IS '所属租户 ID（超管为 NULL）';
COMMENT ON COLUMN "user".username IS '用户名（租户内唯一，超管全局唯一）';
COMMENT ON COLUMN "user".email IS '邮箱（租户内唯一，超管全局唯一）';
COMMENT ON COLUMN "user".password_hash IS '密码哈希';
COMMENT ON COLUMN "user".phone IS '手机号';
COMMENT ON COLUMN "user".status IS '状态：ACTIVE / DISABLED';
COMMENT ON COLUMN "user".user_type IS '用户类型：SUPER_ADMIN / PLATFORM_OPERATOR / TENANT_USER';
COMMENT ON COLUMN "user".created_at IS '创建时间';
COMMENT ON COLUMN "user".updated_at IS '更新时间';

CREATE INDEX idx_user_tenant_id ON "user" (tenant_id);
CREATE INDEX idx_user_user_type ON "user" (user_type);

-- -----------------------------------------------------------
-- 5. 角色表 (role)
-- -----------------------------------------------------------
CREATE TABLE role (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   BIGINT,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(256),
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_code_tenant UNIQUE (code, tenant_id)
);

COMMENT ON TABLE role IS '角色';
COMMENT ON COLUMN role.id IS '主键';
COMMENT ON COLUMN role.tenant_id IS '所属租户 ID（系统角色为 NULL）';
COMMENT ON COLUMN role.code IS '角色编码（租户内唯一）';
COMMENT ON COLUMN role.name IS '角色名称';
COMMENT ON COLUMN role.description IS '角色描述';
COMMENT ON COLUMN role.is_system IS '是否系统内置角色（不可删除）';
COMMENT ON COLUMN role.created_at IS '创建时间';
COMMENT ON COLUMN role.updated_at IS '更新时间';

CREATE INDEX idx_role_tenant_id ON role (tenant_id);

-- -----------------------------------------------------------
-- 6. 权限表 (permission)
-- -----------------------------------------------------------
CREATE TABLE permission (
    id       BIGSERIAL    PRIMARY KEY,
    code     VARCHAR(128) NOT NULL,
    name     VARCHAR(64)  NOT NULL,
    resource VARCHAR(64)  NOT NULL,
    action   VARCHAR(32)  NOT NULL,
    CONSTRAINT uk_permission_code UNIQUE (code)
);

COMMENT ON TABLE permission IS '权限';
COMMENT ON COLUMN permission.id IS '主键';
COMMENT ON COLUMN permission.code IS '权限编码（全局唯一）';
COMMENT ON COLUMN permission.name IS '权限名称';
COMMENT ON COLUMN permission.resource IS '资源类型（tenant, user, role, subscription 等）';
COMMENT ON COLUMN permission.action IS '操作类型（create, read, update, delete, manage）';

CREATE INDEX idx_permission_resource ON permission (resource);

-- -----------------------------------------------------------
-- 7. 用户-角色关联表 (user_role)
-- -----------------------------------------------------------
CREATE TABLE user_role (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT    NOT NULL,
    role_id BIGINT    NOT NULL,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);

COMMENT ON TABLE user_role IS '用户-角色关联';
COMMENT ON COLUMN user_role.id IS '主键';
COMMENT ON COLUMN user_role.user_id IS '用户 ID';
COMMENT ON COLUMN user_role.role_id IS '角色 ID';

CREATE INDEX idx_user_role_role_id ON user_role (role_id);

-- -----------------------------------------------------------
-- 8. 角色-权限关联表 (role_permission)
-- -----------------------------------------------------------
CREATE TABLE role_permission (
    id            BIGSERIAL PRIMARY KEY,
    role_id       BIGINT    NOT NULL,
    permission_id BIGINT    NOT NULL,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);

COMMENT ON TABLE role_permission IS '角色-权限关联';
COMMENT ON COLUMN role_permission.id IS '主键';
COMMENT ON COLUMN role_permission.role_id IS '角色 ID';
COMMENT ON COLUMN role_permission.permission_id IS '权限 ID';

CREATE INDEX idx_role_permission_permission_id ON role_permission (permission_id);

-- -----------------------------------------------------------
-- 9. 审计日志表 (audit_log)
-- -----------------------------------------------------------
CREATE TABLE audit_log (
    id         BIGSERIAL    PRIMARY KEY,
    tenant_id  BIGINT,
    user_id    BIGINT       NOT NULL,
    username   VARCHAR(64)  NOT NULL,
    module     VARCHAR(64)  NOT NULL,
    action     VARCHAR(32)  NOT NULL,
    target     VARCHAR(256),
    detail     JSONB,
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE audit_log IS '审计日志';
COMMENT ON COLUMN audit_log.id IS '主键';
COMMENT ON COLUMN audit_log.tenant_id IS '租户 ID（平台级操作为 NULL）';
COMMENT ON COLUMN audit_log.user_id IS '操作人 ID';
COMMENT ON COLUMN audit_log.username IS '操作人用户名（冗余，防止用户删除后丢失）';
COMMENT ON COLUMN audit_log.module IS '模块名称（auth, user, role, tenant, subscription 等）';
COMMENT ON COLUMN audit_log.action IS '操作类型（create, update, delete, login, logout 等）';
COMMENT ON COLUMN audit_log.target IS '操作对象（如 user:123, role:456）';
COMMENT ON COLUMN audit_log.detail IS '操作详情（JSONB 格式，记录变更前后对比等）';
COMMENT ON COLUMN audit_log.ip_address IS '操作 IP';
COMMENT ON COLUMN audit_log.user_agent IS '客户端标识';
COMMENT ON COLUMN audit_log.created_at IS '操作时间';

CREATE INDEX idx_audit_log_tenant_id ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_user_id ON audit_log (user_id);
CREATE INDEX idx_audit_log_module ON audit_log (module);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);

-- -----------------------------------------------------------
-- 10. 站内信表 (notification)
-- -----------------------------------------------------------
CREATE TABLE notification (
    id         BIGSERIAL    PRIMARY KEY,
    tenant_id  BIGINT,
    user_id    BIGINT,
    title      VARCHAR(256) NOT NULL,
    content    TEXT         NOT NULL,
    type       VARCHAR(32)  NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE notification IS '站内信';
COMMENT ON COLUMN notification.id IS '主键';
COMMENT ON COLUMN notification.tenant_id IS '租户 ID（平台级通知为 NULL）';
COMMENT ON COLUMN notification.user_id IS '接收人 ID（NULL 表示租户下所有用户）';
COMMENT ON COLUMN notification.title IS '通知标题';
COMMENT ON COLUMN notification.content IS '通知内容';
COMMENT ON COLUMN notification.type IS '通知类型：SYSTEM / AUDIT_RESULT / EXPIRY_WARNING / SUBSCRIPTION';
COMMENT ON COLUMN notification.is_read IS '是否已读（false-未读，true-已读）';
COMMENT ON COLUMN notification.read_at IS '阅读时间';
COMMENT ON COLUMN notification.created_at IS '创建时间';

CREATE INDEX idx_notification_tenant_id ON notification (tenant_id);
CREATE INDEX idx_notification_user_id ON notification (user_id);
CREATE INDEX idx_notification_is_read ON notification (is_read);
CREATE INDEX idx_notification_created_at ON notification (created_at);

-- ============================================================
-- 初始数据
-- ============================================================

-- -----------------------------------------------------------
-- 系统权限（Permission 表）
-- 预设所有 resource + action 组合
-- -----------------------------------------------------------
INSERT INTO permission (code, name, resource, action) VALUES
-- 租户管理
('tenant:create',   '创建租户',   'tenant', 'create'),
('tenant:read',     '查看租户',   'tenant', 'read'),
('tenant:update',   '更新租户',   'tenant', 'update'),
('tenant:delete',   '删除租户',   'tenant', 'delete'),
('tenant:manage',   '管理租户',   'tenant', 'manage'),
-- 申请管理
('application:create',  '提交申请',   'application', 'create'),
('application:read',    '查看申请',   'application', 'read'),
('application:approve', '审核通过',   'application', 'approve'),
('application:reject',  '审核拒绝',   'application', 'reject'),
-- 订阅管理
('subscription:create',  '创建订阅',   'subscription', 'create'),
('subscription:read',    '查看订阅',   'subscription', 'read'),
('subscription:update',  '更新订阅',   'subscription', 'update'),
('subscription:manage',  '管理订阅',   'subscription', 'manage'),
-- 用户管理
('user:create',  '创建用户',   'user', 'create'),
('user:read',    '查看用户',   'user', 'read'),
('user:update',  '更新用户',   'user', 'update'),
('user:delete',  '删除用户',   'user', 'delete'),
-- 角色管理
('role:create',  '创建角色',   'role', 'create'),
('role:read',    '查看角色',   'role', 'read'),
('role:update',  '更新角色',   'role', 'update'),
('role:delete',  '删除角色',   'role', 'delete'),
-- 权限管理
('permission:read',    '查看权限',   'permission', 'read'),
('permission:manage',  '管理权限',   'permission', 'manage'),
-- 审计日志
('audit_log:read',  '查看审计日志', 'audit_log', 'read'),
-- 站内信
('notification:create',  '创建通知',   'notification', 'create'),
('notification:read',    '查看通知',   'notification', 'read'),
('notification:manage',  '管理通知',   'notification', 'manage');

-- -----------------------------------------------------------
-- 系统角色（Role 表）
-- SUPER_ADMIN：超级管理员（tenantId=NULL）
-- -----------------------------------------------------------
INSERT INTO role (tenant_id, code, name, description, is_system) VALUES
(NULL, 'SUPER_ADMIN',       '超级管理员', '平台超级管理员，拥有平台级全部管理权限', TRUE),
(NULL, 'PLATFORM_OPERATOR', '平台运营',   '平台运营人员，可审核申请、查看租户、管理订阅', TRUE);

-- -----------------------------------------------------------
-- 超管用户（User 表）
-- admin/admin123，userType=SUPER_ADMIN
-- -----------------------------------------------------------
INSERT INTO "user" (tenant_id, username, email, password_hash, phone, status, user_type) VALUES
(NULL, 'admin', 'admin@converge.local', 'admin123', NULL, 'ACTIVE', 'SUPER_ADMIN');

-- -----------------------------------------------------------
-- 超管角色关联（UserRole 表）
-- 关联超管用户和 SUPER_ADMIN 角色
-- -----------------------------------------------------------
INSERT INTO user_role (user_id, role_id) VALUES
(1, 1);

-- -----------------------------------------------------------
-- 为 SUPER_ADMIN 角色分配全部权限
-- -----------------------------------------------------------
INSERT INTO role_permission (role_id, permission_id)
SELECT 1, id FROM permission;
