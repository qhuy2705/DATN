ALTER TABLE appointments
    ADD COLUMN contact_status VARCHAR(64) NOT NULL DEFAULT 'PHONE_PROVIDED',
    ADD COLUMN patient_response_status VARCHAR(64) NOT NULL DEFAULT 'NONE';

CREATE TABLE appointment_response_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    used_action VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT fk_appointment_response_token_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT uq_appointment_response_token_hash UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_appointment_response_token_appointment
    ON appointment_response_tokens(appointment_id);

CREATE INDEX idx_appointment_response_token_expires
    ON appointment_response_tokens(expires_at, used_at);
