ALTER TABLE appointments
    ADD COLUMN pre_triage_level VARCHAR(32) NULL,
    ADD COLUMN pre_triage_priority VARCHAR(32) NULL,
    ADD COLUMN pre_triage_flags_json TEXT NULL,
    ADD COLUMN pre_triage_reasons_json TEXT NULL,
    ADD COLUMN pre_triage_summary TEXT NULL,
    ADD COLUMN pre_triage_assessed_at DATETIME(6) NULL,
    ADD COLUMN symptom_onset VARCHAR(32) NULL,
    ADD COLUMN chronic_conditions_json TEXT NULL,
    ADD COLUMN functional_impact VARCHAR(32) NULL,
    ADD COLUMN red_flag_selections_json TEXT NULL,
    ADD COLUMN triage_review_status VARCHAR(32) NULL,
    ADD COLUMN triage_reviewed_by BIGINT NULL,
    ADD COLUMN triage_reviewed_at DATETIME(6) NULL,
    ADD COLUMN triage_override_reason VARCHAR(500) NULL;

ALTER TABLE appointments
    ADD CONSTRAINT fk_appointments_triage_reviewed_by
        FOREIGN KEY (triage_reviewed_by) REFERENCES users(id);

CREATE INDEX idx_appt_reception_priority
    ON appointments(branch_id, visit_date, arrival_status, status, triage_priority, eta_start, arrived_at);
