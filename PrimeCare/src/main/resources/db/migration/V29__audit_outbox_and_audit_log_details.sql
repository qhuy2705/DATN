ALTER TABLE audit_logs
    ADD COLUMN event_id VARCHAR(36) NULL,
    ADD COLUMN actor_name VARCHAR(255) NULL,
    ADD COLUMN actor_email VARCHAR(255) NULL;

ALTER TABLE audit_logs
    ADD CONSTRAINT uq_audit_logs_event_id UNIQUE (event_id);

CREATE INDEX idx_audit_logs_action_created
    ON audit_logs (action, created_at);

CREATE INDEX idx_audit_logs_actor_created
    ON audit_logs (actor_id, created_at);

CREATE INDEX idx_audit_logs_actor_role_created
    ON audit_logs (actor_role, created_at);

CREATE TABLE audit_outbox_events (
    id BIGINT AUTO_INCREMENT NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity VARCHAR(64) NOT NULL,
    entity_id BIGINT NULL,
    actor_id BIGINT NULL,
    actor_name VARCHAR(255) NULL,
    actor_email VARCHAR(255) NULL,
    actor_role VARCHAR(32) NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    locked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_audit_outbox_event_id UNIQUE (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_outbox_status_created
    ON audit_outbox_events (status, created_at);

CREATE INDEX idx_audit_outbox_entity_entity_id_created
    ON audit_outbox_events (entity, entity_id, created_at);

CREATE INDEX idx_audit_outbox_action_created
    ON audit_outbox_events (action, created_at);

CREATE INDEX idx_audit_outbox_actor_created
    ON audit_outbox_events (actor_id, created_at);

CREATE INDEX idx_audit_outbox_actor_role_created
    ON audit_outbox_events (actor_role, created_at);
