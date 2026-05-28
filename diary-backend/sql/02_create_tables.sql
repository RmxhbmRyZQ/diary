-- diary_db tables

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id CHAR(36) PRIMARY KEY,
    username VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL UNIQUE,
    auth_key_hash VARCHAR(255) NOT NULL,
    salt_auth VARCHAR(64) NOT NULL,
    encrypted_dek TEXT NOT NULL,
    encrypted_dek_recovery TEXT NOT NULL,
    salt_enc VARCHAR(64) NOT NULL,
    kdf_version INT NOT NULL,
    kdf_params JSON NOT NULL,
    recovery_data TEXT NULL,
    recovery_salt VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Entries table
CREATE TABLE IF NOT EXISTS entries (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    diary_date DATE NOT NULL,
    mood VARCHAR(20) NULL,
    weather VARCHAR(20) NULL,
    favorite TINYINT(1) NOT NULL DEFAULT 0,
    encrypted_payload MEDIUMTEXT NOT NULL,
    iv VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_user_diary_date (user_id, diary_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Attachments table
CREATE TABLE IF NOT EXISTS attachments (
    id CHAR(36) PRIMARY KEY,
    diary_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    iv VARCHAR(32) NOT NULL,
    mime_type VARCHAR(50) NOT NULL,
    sha256 VARCHAR(64) NOT NULL DEFAULT '',
    created_at DATETIME(3) NOT NULL,
    INDEX idx_diary_id (diary_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Sessions table
CREATE TABLE IF NOT EXISTS sessions (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Admin users table (separate from regular users)
CREATE TABLE IF NOT EXISTS admin_users (
    id CHAR(36) PRIMARY KEY,
    username VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
