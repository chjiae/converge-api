-- ============================================================
-- V1__init_schema.sql
-- Converge API 多租户体系 - 数据库初始化脚本
-- 包含所有核心表结构、索引和初始数据
-- ============================================================

-- -----------------------------------------------------------
-- 1. 租户表 (tenant)
-- -----------------------------------------------------------
CREATE TABLE `tenant` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(64)  NOT NULL COMMENT '租户编码（唯一，用于 URL/标识）',
    `name`        VARCHAR(128) NOT NULL COMMENT '租户名称',
    `description` VARCHAR(512) NULL     COMMENT '租户描述',
    `status`      VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / TRIAL / ACTIVE / DISABLED / EXPIRED / DELETED',
    `trial_used`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已使用过试用（0-否，1-是，每个租户只能试用一次）',
    `expired_at`  DATETIME     NULL     COMMENT '到期时间（NULL 表示永不过期）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by`  BIGINT       NULL     COMMENT '创建人（超管 ID 或自注册申请人 ID）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户';

-- -----------------------------------------------------------
-- 2. 租户注册申请表 (tenant_application)
-- -----------------------------------------------------------
CREATE TABLE `tenant_application` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `company_name`     VARCHAR(256) NOT NULL COMMENT '公司/组织名称',
    `contact_name`     VARCHAR(64)  NOT NULL COMMENT '联系人姓名',
    `contact_email`    VARCHAR(128) NOT NULL COMMENT '联系人邮箱',
    `contact_phone`    VARCHAR(32)  NULL     COMMENT '联系人电话',
    `description`      VARCHAR(1024) NULL    COMMENT '用途描述',
    `application_type` VARCHAR(32)  NOT NULL COMMENT '申请类型：REGISTER（注册充值）/ TRIAL（申请试用）',
    `status`           VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / APPROVED / REJECTED',
    `reject_reason`    VARCHAR(512) NULL     COMMENT '拒绝原因',
    `reviewed_by`      BIGINT       NULL     COMMENT '审核人（超管 ID）',
    `reviewed_at`      DATETIME     NULL     COMMENT '审核时间',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_application_status` (`status`),
    KEY `idx_application_contact_email` (`contact_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户注册申请';

-- -----------------------------------------------------------
-- 3. 订阅/订单表 (subscription)
-- -----------------------------------------------------------
CREATE TABLE `subscription` (
    `id`             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      BIGINT         NOT NULL COMMENT '租户 ID',
    `plan_type`      VARCHAR(32)    NOT NULL COMMENT '套餐类型：TRIAL / MONTHLY / QUARTERLY / YEARLY / CUSTOM',
    `amount`         DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '金额（试用为 0）',
    `start_date`     DATE           NOT NULL COMMENT '生效日期',
    `end_date`       DATE           NOT NULL COMMENT '到期日期',
    `status`         VARCHAR(32)    NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / PAID / ACTIVE / EXPIRED / CANCELLED',
    `payment_method` VARCHAR(32)    NULL     COMMENT '支付方式：ALIPAY / WECHAT / CARD_KEY / OFFLINE',
    `payment_ref`    VARCHAR(128)   NULL     COMMENT '支付凭证号',
    `remark`         VARCHAR(512)   NULL     COMMENT '备注',
    `created_by`     BIGINT         NULL     COMMENT '创建人（租户管理员或超管）',
    `created_at`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_subscription_tenant_id` (`tenant_id`),
    KEY `idx_subscription_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订阅/订单';

-- -----------------------------------------------------------
-- 4. 用户表 (user)
-- -----------------------------------------------------------
CREATE TABLE `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     BIGINT       NULL     COMMENT '所属租户 ID（超管为 NULL）',
    `username`      VARCHAR(64)  NOT NULL COMMENT '用户名（租户内唯一，超管全局唯一）',
    `email`         VARCHAR(128) NOT NULL COMMENT '邮箱（租户内唯一，超管全局唯一）',
    `password_hash` VARCHAR(256) NOT NULL COMMENT '密码哈希',
    `phone`         VARCHAR(32)  NULL     COMMENT '手机号',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    `user_type`     VARCHAR(32)  NOT NULL COMMENT '用户类型：SUPER_ADMIN / PLATFORM_OPERATOR / TENANT_USER',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_email_tenant` (`email`, `tenant_id`),
    UNIQUE KEY `uk_user_username_tenant` (`username`, `tenant_id`),
    KEY `idx_user_tenant_id` (`tenant_id`),
    KEY `idx_user_user_type` (`user_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';

-- -----------------------------------------------------------
-- 5. 角色表 (role)
-- -----------------------------------------------------------
CREATE TABLE `role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   BIGINT       NULL     COMMENT '所属租户 ID（系统角色为 NULL）',
    `code`        VARCHAR(64)  NOT NULL COMMENT '角色编码（租户内唯一）',
    `name`        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    `description` VARCHAR(256) NULL     COMMENT '角色描述',
    `is_system`   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否系统内置角色（0-否，1-是，不可删除）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code_tenant` (`code`, `tenant_id`),
    KEY `idx_role_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色';

-- -----------------------------------------------------------
-- 6. 权限表 (permission)
-- -----------------------------------------------------------
CREATE TABLE `permission` (
    `id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`     VARCHAR(128) NOT NULL COMMENT '权限编码（全局唯一）',
    `name`     VARCHAR(64)  NOT NULL COMMENT '权限名称',
    `resource` VARCHAR(64)  NOT NULL COMMENT '资源类型（tenant, user, role, subscription 等）',
    `action`   VARCHAR(32)  NOT NULL COMMENT '操作类型（create, read, update, delete, manage）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`code`),
    KEY `idx_permission_resource` (`resource`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限';

-- -----------------------------------------------------------
-- 7. 用户-角色关联表 (user_role)
-- -----------------------------------------------------------
CREATE TABLE `user_role` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `role_id` BIGINT NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_role_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联';

-- -----------------------------------------------------------
-- 8. 角色-权限关联表 (role_permission)
-- -----------------------------------------------------------
CREATE TABLE `role_permission` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       BIGINT NOT NULL COMMENT '角色 ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限 ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_role_permission_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联';

-- -----------------------------------------------------------
-- 9. 审计日志表 (audit_log)
-- -----------------------------------------------------------
CREATE TABLE `audit_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`  BIGINT       NULL     COMMENT '租户 ID（平台级操作为 NULL）',
    `user_id`    BIGINT       NOT NULL COMMENT '操作人 ID',
    `username`   VARCHAR(64)  NOT NULL COMMENT '操作人用户名（冗余，防止用户删除后丢失）',
    `module`     VARCHAR(64)  NOT NULL COMMENT '模块名称（auth, user, role, tenant, subscription 等）',
    `action`     VARCHAR(32)  NOT NULL COMMENT '操作类型（create, update, delete, login, logout 等）',
    `target`     VARCHAR(256) NULL     COMMENT '操作对象（如 user:123, role:456）',
    `detail`     JSON         NULL     COMMENT '操作详情（变更前后对比等）',
    `ip_address` VARCHAR(64)  NULL     COMMENT '操作 IP',
    `user_agent` VARCHAR(512) NULL     COMMENT '客户端标识',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_log_tenant_id` (`tenant_id`),
    KEY `idx_audit_log_user_id` (`user_id`),
    KEY `idx_audit_log_module` (`module`),
    KEY `idx_audit_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志';

-- -----------------------------------------------------------
-- 10. 站内信表 (notification)
-- -----------------------------------------------------------
CREATE TABLE `notification` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`  BIGINT       NULL     COMMENT '租户 ID（平台级通知为 NULL）',
    `user_id`    BIGINT       NULL     COMMENT '接收人 ID（NULL 表示租户下所有用户）',
    `title`      VARCHAR(256) NOT NULL COMMENT '通知标题',
    `content`    TEXT         NOT NULL COMMENT '通知内容',
    `type`       VARCHAR(32)  NOT NULL COMMENT '通知类型：SYSTEM / AUDIT_RESULT / EXPIRY_WARNING / SUBSCRIPTION',
    `is_read`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已读（0-未读，1-已读）',
    `read_at`    DATETIME     NULL     COMMENT '阅读时间',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_notification_tenant_id` (`tenant_id`),
    KEY `idx_notification_user_id` (`user_id`),
    KEY `idx_notification_is_read` (`is_read`),
    KEY `idx_notification_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内信';

-- ============================================================
-- 初始数据
-- ============================================================

-- -----------------------------------------------------------
-- 系统权限（Permission 表）
-- 预设所有 resource + action 组合
-- -----------------------------------------------------------
INSERT INTO `permission` (`code`, `name`, `resource`, `action`) VALUES
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
INSERT INTO `role` (`tenant_id`, `code`, `name`, `description`, `is_system`) VALUES
(NULL, 'SUPER_ADMIN',       '超级管理员', '平台超级管理员，拥有平台级全部管理权限', 1),
(NULL, 'PLATFORM_OPERATOR', '平台运营',   '平台运营人员，可审核申请、查看租户、管理订阅', 1);

-- -----------------------------------------------------------
-- 超管用户（User 表）
-- admin/admin123，userType=SUPER_ADMIN
-- 密码暂时用明文，后续 Task 7 会使用 BCrypt 加密
-- -----------------------------------------------------------
INSERT INTO `user` (`tenant_id`, `username`, `email`, `password_hash`, `phone`, `status`, `user_type`) VALUES
(NULL, 'admin', 'admin@converge.local', 'admin123', NULL, 'ACTIVE', 'SUPER_ADMIN');

-- -----------------------------------------------------------
-- 超管角色关联（UserRole 表）
-- 关联超管用户和 SUPER_ADMIN 角色
-- -----------------------------------------------------------
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES
(1, 1);

-- -----------------------------------------------------------
-- 为 SUPER_ADMIN 角色分配全部权限
-- -----------------------------------------------------------
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `permission`;
