-- ============================================================
-- V8: Drug-Drug Interactions, Encounter Reopen, Medication Inventory
-- ============================================================

-- Drug-Drug Interaction table
CREATE TABLE drug_interactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    medication_a_id BIGINT NOT NULL,
    medication_b_id BIGINT NOT NULL,
    severity VARCHAR(32) NOT NULL COMMENT 'MINOR, MODERATE, SEVERE, CONTRAINDICATED',
    description TEXT,
    clinical_effect TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT fk_drug_interact_med_a FOREIGN KEY (medication_a_id) REFERENCES medications(id),
    CONSTRAINT fk_drug_interact_med_b FOREIGN KEY (medication_b_id) REFERENCES medications(id),
    CONSTRAINT uq_drug_interaction_pair UNIQUE (medication_a_id, medication_b_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Encounter Reopen Logs
CREATE TABLE encounter_reopen_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    encounter_id BIGINT NOT NULL,
    reopened_by_user_id BIGINT NOT NULL,
    reason TEXT NOT NULL,
    previous_status VARCHAR(32) NOT NULL,
    reopened_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reopen_encounter FOREIGN KEY (encounter_id) REFERENCES encounters(id),
    CONSTRAINT fk_reopen_user FOREIGN KEY (reopened_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Expand Medication entity
ALTER TABLE medications
    ADD COLUMN category VARCHAR(64) NULL AFTER contraindication_note,
    ADD COLUMN route_of_administration VARCHAR(64) NULL AFTER category,
    ADD COLUMN storage_conditions VARCHAR(128) NULL AFTER route_of_administration,
    ADD COLUMN requires_prescription BOOLEAN NOT NULL DEFAULT TRUE AFTER storage_conditions,
    ADD COLUMN max_daily_dose VARCHAR(128) NULL AFTER requires_prescription,
    ADD COLUMN unit_price BIGINT NULL AFTER max_daily_dose;

-- Medication Batch (Lô thuốc)
CREATE TABLE medication_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    medication_id BIGINT NOT NULL,
    batch_number VARCHAR(64) NOT NULL,
    quantity_in_stock INT NOT NULL DEFAULT 0,
    manufacture_date DATE NULL,
    expiry_date DATE NULL,
    import_price BIGINT NULL,
    sell_price BIGINT NULL,
    supplier VARCHAR(255) NULL,
    note TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT fk_batch_medication FOREIGN KEY (medication_id) REFERENCES medications(id),
    CONSTRAINT uq_batch_number UNIQUE (medication_id, batch_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_batch_medication ON medication_batches(medication_id);
CREATE INDEX idx_batch_expiry ON medication_batches(expiry_date);

-- Inventory Transactions (Phiếu xuất nhập kho)
CREATE TABLE inventory_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    transaction_type VARCHAR(32) NOT NULL COMMENT 'IMPORT, EXPORT, ADJUSTMENT, DISPENSED, EXPIRED',
    quantity INT NOT NULL,
    reason TEXT NULL,
    reference_type VARCHAR(32) NULL COMMENT 'PRESCRIPTION, MANUAL',
    reference_id BIGINT NULL,
    performed_by_user_id BIGINT NULL,
    performed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT fk_inv_txn_batch FOREIGN KEY (batch_id) REFERENCES medication_batches(id),
    CONSTRAINT fk_inv_txn_user FOREIGN KEY (performed_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_inv_txn_batch ON inventory_transactions(batch_id);

-- Refund Records
CREATE TABLE refund_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    refund_amount BIGINT NOT NULL,
    reason TEXT NOT NULL,
    approved_by_user_id BIGINT NULL,
    refunded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT fk_refund_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT fk_refund_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE encounters ADD COLUMN reopened_count INT NOT NULL DEFAULT 0 AFTER completed_at;
