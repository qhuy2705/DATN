-- ============================================================
-- V9: Bank transfer reconciliation / payment intents
-- ============================================================

ALTER TABLE invoices
    MODIFY COLUMN payment_status VARCHAR(32) NOT NULL;

ALTER TABLE invoices
    ADD COLUMN payment_reference VARCHAR(32) NULL AFTER payment_status,
    ADD COLUMN transfer_content VARCHAR(64) NULL AFTER payment_reference,
    ADD COLUMN payment_detected_at DATETIME NULL AFTER transfer_content,
    ADD COLUMN payment_review_reason VARCHAR(255) NULL AFTER payment_detected_at;

CREATE UNIQUE INDEX uq_invoice_payment_reference ON invoices(payment_reference);

CREATE TABLE payment_intents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    payment_reference VARCHAR(32) NOT NULL,
    transfer_content VARCHAR(64) NOT NULL,
    bank_code VARCHAR(32) NULL,
    bank_account_no VARCHAR(64) NULL,
    bank_account_name VARCHAR(255) NULL,
    expected_amount BIGINT NOT NULL,
    qr_payload TEXT NULL,
    status VARCHAR(24) NOT NULL,
    expires_at DATETIME NULL,
    detected_at DATETIME NULL,
    confirmed_at DATETIME NULL,
    matched_transaction_ref VARCHAR(128) NULL,
    review_reason VARCHAR(255) NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT fk_payment_intent_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT uq_payment_intent_invoice UNIQUE (invoice_id),
    CONSTRAINT uq_payment_intent_reference UNIQUE (payment_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bank_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    transaction_ref VARCHAR(128) NOT NULL,
    amount BIGINT NOT NULL,
    transfer_content VARCHAR(255) NULL,
    bank_account_no VARCHAR(64) NULL,
    bank_code VARCHAR(32) NULL,
    transaction_time DATETIME NULL,
    status VARCHAR(24) NOT NULL,
    matched_invoice_id BIGINT NULL,
    matched_at DATETIME NULL,
    review_reason VARCHAR(255) NULL,
    raw_payload TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT fk_bank_transaction_invoice FOREIGN KEY (matched_invoice_id) REFERENCES invoices(id),
    CONSTRAINT uq_bank_transaction_ref UNIQUE (transaction_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_payment_intent_amount_status ON payment_intents(expected_amount, status);
CREATE INDEX idx_bank_transaction_status ON bank_transactions(status);
