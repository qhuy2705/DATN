ALTER TABLE appointments
    ADD COLUMN checked_in_late TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN late_minutes INT NULL;

UPDATE appointments
SET late_minutes = 0
WHERE late_minutes IS NULL;

ALTER TABLE appointments
    MODIFY COLUMN late_minutes INT NOT NULL DEFAULT 0;

CREATE INDEX idx_appt_overdue_eta_end
    ON appointments(status, visit_date, eta_end, arrival_status, checked_in_at);
