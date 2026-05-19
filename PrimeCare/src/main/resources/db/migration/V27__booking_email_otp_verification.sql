ALTER TABLE users
    ADD COLUMN email_verified_at DATETIME(6) NULL;

CREATE TABLE booking_email_otps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    normalized_email VARCHAR(255) NOT NULL,
    verification_id VARCHAR(64) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    verified_at DATETIME(6) NULL,
    consumed_at DATETIME(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    token_hash VARCHAR(255) NULL,
    token_expires_at DATETIME(6) NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT uq_booking_email_otp_verification_id UNIQUE (verification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_booking_email_otp_email
    ON booking_email_otps(normalized_email, created_at);

CREATE INDEX idx_booking_email_otp_status
    ON booking_email_otps(status, expires_at);
