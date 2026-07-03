-- ============================================================
-- V3__add_admin_fields_to_tenant_application.sql
-- 为租户申请表添加管理员账号信息字段，用于审核通过时自动创建管理员
-- ============================================================

ALTER TABLE `tenant_application`
    ADD COLUMN `admin_username` VARCHAR(64)  NULL COMMENT '管理员用户名（审核通过时用于创建管理员账号）' AFTER `description`,
    ADD COLUMN `admin_email`    VARCHAR(128) NULL COMMENT '管理员邮箱（审核通过时用于创建管理员账号）' AFTER `admin_username`,
    ADD COLUMN `admin_password` VARCHAR(256) NULL COMMENT '管理员密码（审核通过时用于创建管理员账号）' AFTER `admin_email`;
