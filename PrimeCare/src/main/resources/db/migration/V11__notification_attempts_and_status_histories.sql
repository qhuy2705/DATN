ALTER TABLE notifications
    ADD COLUMN entity_type VARCHAR(32) NULL,
    ADD COLUMN entity_id BIGINT NULL,
    ADD COLUMN scheduled_at DATETIME NULL,
    ADD COLUMN last_attempt_at DATETIME NULL,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_notifications_entity ON notifications(entity_type, entity_id);
CREATE INDEX idx_notifications_scheduled ON notifications(status, scheduled_at);

CREATE TABLE IF NOT EXISTS notification_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider_message_id VARCHAR(128) NULL,
    error_message VARCHAR(500) NULL,
    response_excerpt VARCHAR(1000) NULL,
    attempted_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_attempts_notification FOREIGN KEY (notification_id) REFERENCES notifications(id)
);

CREATE INDEX idx_notification_attempts_notification ON notification_attempts(notification_id, attempt_no);

CREATE TABLE IF NOT EXISTS appointment_status_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    changed_by BIGINT NULL,
    changed_at DATETIME NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_appointment_status_history_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_appointment_status_history_user FOREIGN KEY (changed_by) REFERENCES users(id)
);
CREATE INDEX idx_appointment_status_history_appt ON appointment_status_histories(appointment_id, changed_at);

CREATE TABLE IF NOT EXISTS invoice_status_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    from_status VARCHAR(16) NULL,
    to_status VARCHAR(16) NOT NULL,
    changed_by BIGINT NULL,
    changed_at DATETIME NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_invoice_status_history_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT fk_invoice_status_history_user FOREIGN KEY (changed_by) REFERENCES users(id)
);
CREATE INDEX idx_invoice_status_history_invoice ON invoice_status_histories(invoice_id, changed_at);

CREATE TABLE IF NOT EXISTS service_result_status_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_result_id BIGINT NOT NULL,
    from_status VARCHAR(16) NULL,
    to_status VARCHAR(16) NOT NULL,
    changed_by BIGINT NULL,
    changed_at DATETIME NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_service_result_status_history_result FOREIGN KEY (service_result_id) REFERENCES service_results(id),
    CONSTRAINT fk_service_result_status_history_user FOREIGN KEY (changed_by) REFERENCES users(id)
);
CREATE INDEX idx_service_result_status_history_result ON service_result_status_histories(service_result_id, changed_at);
