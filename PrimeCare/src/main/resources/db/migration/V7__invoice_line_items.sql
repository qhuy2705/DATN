-- ============================================================
-- V7: Invoice Line Items & Tax Support
-- Adds invoice_items table and tax column to invoices
-- ============================================================

ALTER TABLE invoices
    ADD COLUMN tax_amount BIGINT NOT NULL DEFAULT 0 AFTER discount_amount;

CREATE TABLE invoice_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    reference_type VARCHAR(32) NOT NULL,
    reference_id BIGINT NULL,
    name_snapshot VARCHAR(255) NOT NULL,
    unit_price BIGINT NOT NULL,
    quantity INT NOT NULL,
    tax_rate DECIMAL(5,2) DEFAULT 0.00,
    subtotal_amount BIGINT NOT NULL,
    tax_amount BIGINT NOT NULL,
    total_amount BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME NULL,
    deleted_by BIGINT NULL,
    CONSTRAINT fk_invoice_item_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_invoice_item_invoice ON invoice_items(invoice_id);
