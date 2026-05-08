-- ============================================================
-- V5: Expand Patient Profile
-- Adds clinical and demographic depth to the patient entity
-- ============================================================

ALTER TABLE patients
    ADD COLUMN blood_type VARCHAR(16) NULL,
    ADD COLUMN ethnicity VARCHAR(64) NULL,
    ADD COLUMN nationality VARCHAR(64) NOT NULL DEFAULT 'Việt Nam',
    ADD COLUMN occupation VARCHAR(255) NULL,
    ADD COLUMN province VARCHAR(128) NULL,
    ADD COLUMN district VARCHAR(128) NULL,
    ADD COLUMN ward VARCHAR(128) NULL,
    ADD COLUMN insurance_expiry_date DATE NULL,
    ADD COLUMN insurance_registered_hospital VARCHAR(500) NULL;

-- Index for searching patients by province/district for reporting
CREATE INDEX idx_patient_location ON patients(province, district);

-- Index for insurance number for quick lookup
-- (Already exists in initial script, but adding a check just in case)
-- CREATE INDEX idx_patient_insurance_number ON patients(insurance_number);
