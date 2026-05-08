-- ============================================================
-- V6: Patient Allergies
-- Creates patient_allergies table for clinical safety
-- ============================================================

CREATE TABLE patient_allergies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    allergen_name VARCHAR(255) NOT NULL,
    allergy_type VARCHAR(32) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    reaction TEXT,
    noted_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT fk_pat_allergy_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT fk_pat_allergy_noted_by FOREIGN KEY (noted_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_pat_allergy_patient ON patient_allergies(patient_id);
