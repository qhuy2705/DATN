ALTER TABLE users
    ADD COLUMN patient_id BIGINT NULL;

ALTER TABLE users
    ADD CONSTRAINT uq_users_patient UNIQUE (patient_id);

ALTER TABLE users
    ADD CONSTRAINT fk_users_patient FOREIGN KEY (patient_id) REFERENCES patients(id);

CREATE TABLE IF NOT EXISTS notification_preferences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    allow_email BIT NOT NULL DEFAULT 1,
    allow_sms BIT NOT NULL DEFAULT 1,
    appointment_reminders BIT NOT NULL DEFAULT 1,
    result_ready_alerts BIT NOT NULL DEFAULT 1,
    invoice_updates BIT NOT NULL DEFAULT 1,
    security_alerts BIT NOT NULL DEFAULT 1,
    preferred_channel VARCHAR(16) NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_notification_preferences_user UNIQUE (user_id),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users(id)
);
