CREATE TABLE IF NOT EXISTS internal_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    route VARCHAR(500) NULL,
    entity_type VARCHAR(64) NULL,
    entity_id BIGINT NULL,
    metadata_json LONGTEXT NULL,
    read_at DATETIME(6) NULL,
    expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_internal_notifications_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_internal_notif_recipient_read_created
    ON internal_notifications(recipient_user_id, read_at, created_at);

CREATE INDEX idx_internal_notif_recipient_created
    ON internal_notifications(recipient_user_id, created_at);

CREATE INDEX idx_internal_notif_entity
    ON internal_notifications(entity_type, entity_id);

ALTER TABLE appointments
    ADD COLUMN no_show_note VARCHAR(500) NULL;

CREATE INDEX idx_appt_overdue_scan
    ON appointments(status, visit_date, eta_start, arrival_status, checked_in_at);
