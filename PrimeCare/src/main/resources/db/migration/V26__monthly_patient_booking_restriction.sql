CREATE TABLE patient_violation_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NULL,
    appointment_id BIGINT NULL,
    identity_key_hash VARCHAR(64) NOT NULL,
    period_month VARCHAR(7) NOT NULL,
    type VARCHAR(64) NOT NULL,
    source VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    points INT NOT NULL,
    note TEXT NULL,
    created_by BIGINT NULL,
    voided_by BIGINT NULL,
    voided_at DATETIME(6) NULL,
    void_reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT fk_violation_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT fk_violation_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_violation_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_violation_voided_by FOREIGN KEY (voided_by) REFERENCES users(id),
    CONSTRAINT uq_patient_violation_event_appt_type_status UNIQUE (appointment_id, type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_violation_identity_month_status
    ON patient_violation_events(identity_key_hash, period_month, status);

CREATE INDEX idx_violation_appointment_type_status
    ON patient_violation_events(appointment_id, type, status);

CREATE TABLE patient_booking_restrictions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NULL,
    identity_key_hash VARCHAR(64) NOT NULL,
    period_month VARCHAR(7) NOT NULL,
    level VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    score_snapshot INT NOT NULL,
    reason VARCHAR(1000) NULL,
    starts_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    lifted_by BIGINT NULL,
    lifted_at DATETIME(6) NULL,
    lift_reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT fk_restriction_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT fk_restriction_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_restriction_lifted_by FOREIGN KEY (lifted_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_restriction_identity_status_expires
    ON patient_booking_restrictions(identity_key_hash, status, expires_at);

CREATE INDEX idx_restriction_period_level
    ON patient_booking_restrictions(period_month, level);

CREATE TABLE booking_restriction_overrides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NULL,
    identity_key_hash VARCHAR(64) NOT NULL,
    restriction_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    reason VARCHAR(1000) NULL,
    created_by BIGINT NULL,
    used_at DATETIME(6) NULL,
    used_appointment_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT fk_override_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT fk_override_restriction FOREIGN KEY (restriction_id) REFERENCES patient_booking_restrictions(id),
    CONSTRAINT fk_override_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_override_used_appointment FOREIGN KEY (used_appointment_id) REFERENCES appointments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_restriction_override_identity_status_expires
    ON booking_restriction_overrides(identity_key_hash, status, expires_at);
