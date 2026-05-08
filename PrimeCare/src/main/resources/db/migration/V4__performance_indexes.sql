-- ============================================================
-- V4: Performance Indexes
-- Adds composite and single-column indexes for frequently queried patterns
-- ============================================================

-- ==================== Appointments ====================
-- Staff appointment list: filtered by visit_date + status (most common query)
CREATE INDEX idx_appt_visit_date_status ON appointments(visit_date, status);

-- Doctor calendar: filtered by doctor + date range + session
CREATE INDEX idx_appt_doctor_visit_session ON appointments(doctor_id, visit_date, session);

-- Patient appointment history
CREATE INDEX idx_appt_patient_id ON appointments(patient_id);

-- Reception queue: branch + visit_date + arrival_status
CREATE INDEX idx_appt_branch_visit_arrival ON appointments(branch_id, visit_date, arrival_status);

-- Slot availability check
CREATE INDEX idx_appt_doctor_date_session_eta ON appointments(doctor_id, visit_date, session, eta_start);

-- ==================== Encounters ====================
-- Doctor's active encounters
CREATE INDEX idx_encounter_doctor_status ON encounters(doctor_id, status);

-- Patient encounter history
CREATE INDEX idx_encounter_patient_id ON encounters(patient_id);

-- Time-based queries (dashboard)
CREATE INDEX idx_encounter_started_at ON encounters(started_at);

-- ==================== Invoices ====================
-- Cashier list: filter by payment_status
CREATE INDEX idx_invoice_payment_status ON invoices(payment_status);

-- Revenue report: paid_at range
CREATE INDEX idx_invoice_paid_at ON invoices(paid_at);

-- VNPAY callback lookup
CREATE INDEX idx_invoice_vnp_txn_ref ON invoices(vnp_txn_ref);

-- ==================== Service Orders ====================
-- Doctor/encounter lookup
CREATE INDEX idx_so_encounter_id ON service_orders(encounter_id);

-- Cashier queue: payment_status + status
CREATE INDEX idx_so_payment_status ON service_orders(payment_status, status);

-- ==================== Service Order Items ====================
-- Department queue lookup
CREATE INDEX idx_soi_dept_status ON service_order_items(assigned_department_code, status);

-- Service order lookup
CREATE INDEX idx_soi_service_order_id ON service_order_items(service_order_id);

-- ==================== Service Results ====================
-- Item lookup
CREATE INDEX idx_sr_item_id ON service_results(service_order_item_id);

-- Status filter
CREATE INDEX idx_sr_status ON service_results(status);

-- ==================== Prescriptions ====================
-- Encounter prescriptions
CREATE INDEX idx_prescription_encounter ON prescriptions(encounter_id);

-- Status filter
CREATE INDEX idx_prescription_status ON prescriptions(status);

-- ==================== Doctor Work Schedules ====================
-- Schedule lookup
CREATE INDEX idx_dws_doctor_date ON doctor_work_schedules(doctor_id, work_date);
