-- Seed data for admin_users
-- Default admin account: username=admin, password=Admin@Diary2026!
-- IMPORTANT: Change the password immediately after first login.
-- The password_hash below is a bcrypt hash of 'Admin@Diary2026!' with cost 12.
INSERT INTO admin_users (id, username, password_hash, created_at)
VALUES (
    UUID(),
    'admin',
    '$2a$12$A.GDAYktsE/udQYF3us9.etwoYkx9QTG01CKRR/ucuRG9XUCjqawS',
    UTC_TIMESTAMP(3)
) ON DUPLICATE KEY UPDATE username = username;
