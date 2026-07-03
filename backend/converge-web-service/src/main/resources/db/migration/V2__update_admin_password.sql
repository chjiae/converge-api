-- V2__update_admin_password.sql
-- 更新超管密码为 BCrypt 加密后的 admin123
UPDATE "user" SET password_hash = '$2a$10$jhpcfLzvgVUjqtYKq/LgEe9ZnInQmjMXdPtUu1SahmicphJ654BSi' WHERE username = 'admin';
