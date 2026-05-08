-- V15: Product performance indexes and department code normalization.
-- MySQL does not support CREATE INDEX IF NOT EXISTS consistently across deployments,
-- so each index is created only when the target table/columns exist and the index name is absent.

UPDATE medical_services
SET department_code = UPPER(TRIM(department_code))
WHERE department_code IS NOT NULL
  AND department_code <> UPPER(TRIM(department_code));

UPDATE service_order_items
SET assigned_department_code = UPPER(TRIM(assigned_department_code))
WHERE assigned_department_code IS NOT NULL
  AND assigned_department_code <> UPPER(TRIM(assigned_department_code));

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'appointments' AND index_name = 'idx_appt_availability_lookup'
    )
    OR (
      SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'appointments'
        AND column_name IN ('doctor_id', 'visit_date', 'session', 'status', 'eta_start', 'eta_end')
    ) <> 6,
    'SELECT 1',
    'CREATE INDEX idx_appt_availability_lookup ON appointments(doctor_id, visit_date, session, status, eta_start, eta_end)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'encounters' AND index_name = 'idx_encounter_doctor_status_started'
    )
    OR (
      SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'encounters'
        AND column_name IN ('doctor_id', 'status', 'started_at')
    ) <> 3,
    'SELECT 1',
    'CREATE INDEX idx_encounter_doctor_status_started ON encounters(doctor_id, status, started_at)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'appointments' AND index_name = 'idx_appt_doctor_visit_status_eta'
    )
    OR (
      SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'appointments'
        AND column_name IN ('doctor_id', 'visit_date', 'status', 'eta_start')
    ) <> 4,
    'SELECT 1',
    'CREATE INDEX idx_appt_doctor_visit_status_eta ON appointments(doctor_id, visit_date, status, eta_start)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'service_order_items' AND index_name = 'idx_soi_dept_status_result_queue_completed'
    )
    OR (
      SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'service_order_items'
        AND column_name IN ('assigned_department_code', 'status', 'result_status', 'queued_at', 'completed_at')
    ) <> 5,
    'SELECT 1',
    'CREATE INDEX idx_soi_dept_status_result_queue_completed ON service_order_items(assigned_department_code, status, result_status, queued_at, completed_at)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'service_orders' AND index_name = 'idx_so_payment_created'
    )
    OR (
      SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'service_orders'
        AND column_name IN ('payment_status', 'created_at')
    ) <> 2,
    'SELECT 1',
    'CREATE INDEX idx_so_payment_created ON service_orders(payment_status, created_at)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'invoices' AND index_name = 'idx_invoice_payment_created'
    )
    OR (
      SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'invoices'
        AND column_name IN ('payment_status', 'created_at')
    ) <> 2,
    'SELECT 1',
    'CREATE INDEX idx_invoice_payment_created ON invoices(payment_status, created_at)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'prescriptions' AND index_name = 'idx_prescription_status_issued_date'
    )
    OR (
      SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'prescriptions'
        AND column_name IN ('status', 'issued_date')
    ) <> 2,
    'SELECT 1',
    'CREATE INDEX idx_prescription_status_issued_date ON prescriptions(status, issued_date)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Covered by existing unique constraints/indexes:
-- encounters(appointment_id), invoices(code), prescriptions(code), service_orders(encounter_id),
-- service_order_items(service_order_id), prescriptions(encounter_id).
