-- ═══════════════════════════════════════════════════════════
--  Secure Push Gateway — MySQL Schema
--  Run once to bootstrap the database, then let JPA manage it.
-- ═══════════════════════════════════════════════════════════

CREATE DATABASE IF NOT EXISTS securepushgateway
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE securepushgateway;

-- ─── Users ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        ENUM('OWNER','DEVELOPER') NOT NULL DEFAULT 'DEVELOPER',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_users_role (role)
) ENGINE=InnoDB;

-- ─── Scans ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS scans (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    developer_id          BIGINT NOT NULL,
    repo_name             VARCHAR(255) NOT NULL,
    commit_sha            VARCHAR(64)  NOT NULL,
    branch                VARCHAR(128) NOT NULL DEFAULT 'main',
    status                ENUM('PASS','FAIL','PENDING') NOT NULL DEFAULT 'PENDING',
    scanned_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_vulnerabilities INT NOT NULL DEFAULT 0,
    FOREIGN KEY (developer_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_scans_developer  (developer_id),
    INDEX idx_scans_status     (status),
    INDEX idx_scans_scanned_at (scanned_at)
) ENGINE=InnoDB;

-- ─── Vulnerability Results ─────────────────────────────────
CREATE TABLE IF NOT EXISTS vulnerability_results (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    scan_id      BIGINT       NOT NULL,
    rule_id      VARCHAR(16)  NOT NULL,
    severity     ENUM('CRITICAL','HIGH','MEDIUM','LOW') NOT NULL,
    file_name    VARCHAR(512) NOT NULL,
    line_number  INT NOT NULL DEFAULT 0,
    description  TEXT         NOT NULL,
    code_snippet TEXT,
    FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE,
    INDEX idx_vulns_scan     (scan_id),
    INDEX idx_vulns_severity (severity),
    INDEX idx_vulns_rule     (rule_id)
) ENGINE=InnoDB;

-- ─── Badges ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS badges (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    developer_id BIGINT NOT NULL,
    badge_type   ENUM('FIRST_CLEAN_PUSH','STREAK_3','STREAK_5','STREAK_10','CLEAN_WEEK') NOT NULL,
    awarded_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scan_id      BIGINT,
    FOREIGN KEY (developer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (scan_id)      REFERENCES scans(id) ON DELETE SET NULL,
    INDEX idx_badges_developer (developer_id),
    UNIQUE KEY uq_badge_per_dev (developer_id, badge_type)
) ENGINE=InnoDB;

-- ─── Notifications ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    message    VARCHAR(500) NOT NULL,
    type       ENUM('SCAN_PASS','SCAN_FAIL','BADGE_AWARDED') NOT NULL,
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    scan_id    BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notifs_user    (user_id),
    INDEX idx_notifs_is_read (user_id, is_read)
) ENGINE=InnoDB;

-- ─── Seed: Owner account ───────────────────────────────────
-- Password is bcrypt of 'admin123' — CHANGE IN PRODUCTION
INSERT IGNORE INTO users (username, email, password, role)
VALUES ('admin', 'admin@securepush.io',
        '$2a$12$Lf7sKTFzpEwbNgVXJhz6IuKjNr5ZT9e2mFv5RMeQ4pXPYAiydY3Dm', 'OWNER');
