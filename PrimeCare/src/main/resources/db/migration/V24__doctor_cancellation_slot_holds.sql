ALTER TABLE appointments
    ADD COLUMN cancellation_reason_type VARCHAR(64) NULL,
    ADD COLUMN follow_up_type VARCHAR(64) NULL;

CREATE TABLE appointment_slot_holds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_appointment_id BIGINT NOT NULL,
    patient_id BIGINT NULL,
    doctor_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    specialty_id BIGINT NOT NULL,
    visit_date DATE NOT NULL,
    session VARCHAR(8) NOT NULL,
    slot_start TIME NOT NULL,
    slot_end TIME NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at DATETIME NOT NULL,
    reminder_queued_at DATETIME NULL,
    accepted_at DATETIME NULL,
    contact_requested_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    expired_at DATETIME NULL,
    hold_reason VARCHAR(64) NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    token_nonce VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_slot_hold_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_slot_hold_original_appointment
        FOREIGN KEY (original_appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_slot_hold_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT fk_slot_hold_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctor_profiles(id),
    CONSTRAINT fk_slot_hold_branch
        FOREIGN KEY (branch_id) REFERENCES branches(id),
    CONSTRAINT fk_slot_hold_specialty
        FOREIGN KEY (specialty_id) REFERENCES specialties(id)
);

CREATE INDEX idx_slot_hold_doctor_slot
    ON appointment_slot_holds (doctor_id, visit_date, session, slot_start, slot_end, status, expires_at);

CREATE INDEX idx_slot_hold_status_expires
    ON appointment_slot_holds (status, expires_at);
