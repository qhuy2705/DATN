ALTER TABLE appointments
    ADD COLUMN chronic_condition_others_json TEXT NULL,
    ADD COLUMN pre_triage_matched_terms_json TEXT NULL,
    ADD COLUMN pre_triage_matched_rules_json TEXT NULL,
    ADD COLUMN pre_triage_source VARCHAR(32) NULL,
    ADD COLUMN pre_triage_confidence_level VARCHAR(32) NULL,
    ADD COLUMN pre_triage_knowledge_base_version VARCHAR(64) NULL,
    ADD COLUMN pre_triage_ruleset_version VARCHAR(64) NULL,
    ADD COLUMN pre_triage_ai_model_version VARCHAR(128) NULL;

CREATE TABLE triage_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id BIGINT NULL,
    actor_name VARCHAR(255) NULL,
    action VARCHAR(64) NOT NULL,
    from_priority VARCHAR(32) NULL,
    to_priority VARCHAR(32) NULL,
    reason TEXT NULL,
    snapshot_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_triage_audit_logs_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments(id)
);

CREATE INDEX idx_triage_audit_logs_appointment_created
    ON triage_audit_logs (appointment_id, created_at);
