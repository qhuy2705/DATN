-- ============================================================
-- V2: Soft Delete + Optimistic Locking for all entities
-- Adds: version, deleted, deleted_at, deleted_by columns
-- ============================================================

-- Appointments
ALTER TABLE appointments ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE appointments ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE appointments ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE appointments ADD COLUMN deleted_by BIGINT NULL;
CREATE INDEX idx_appointments_deleted ON appointments(deleted);

-- Encounters
ALTER TABLE encounters ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE encounters ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE encounters ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE encounters ADD COLUMN deleted_by BIGINT NULL;
CREATE INDEX idx_encounters_deleted ON encounters(deleted);

-- Invoices
ALTER TABLE invoices ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE invoices ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE invoices ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE invoices ADD COLUMN deleted_by BIGINT NULL;
CREATE INDEX idx_invoices_deleted ON invoices(deleted);

-- Patients
ALTER TABLE patients ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE patients ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE patients ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE patients ADD COLUMN deleted_by BIGINT NULL;
CREATE INDEX idx_patients_deleted ON patients(deleted);

-- Prescriptions
ALTER TABLE prescriptions ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE prescriptions ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE prescriptions ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE prescriptions ADD COLUMN deleted_by BIGINT NULL;
CREATE INDEX idx_prescriptions_deleted ON prescriptions(deleted);

-- Service Orders
ALTER TABLE service_orders ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE service_orders ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE service_orders ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE service_orders ADD COLUMN deleted_by BIGINT NULL;
CREATE INDEX idx_service_orders_deleted ON service_orders(deleted);

-- Service Order Items
ALTER TABLE service_order_items ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE service_order_items ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE service_order_items ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE service_order_items ADD COLUMN deleted_by BIGINT NULL;
CREATE INDEX idx_service_order_items_deleted ON service_order_items(deleted);

-- Service Results
ALTER TABLE service_results ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE service_results ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE service_results ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE service_results ADD COLUMN deleted_by BIGINT NULL;
CREATE INDEX idx_service_results_deleted ON service_results(deleted);
