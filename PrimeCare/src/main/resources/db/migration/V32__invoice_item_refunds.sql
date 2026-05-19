ALTER TABLE service_orders
    MODIFY COLUMN payment_status VARCHAR(32) NOT NULL;

ALTER TABLE invoice_status_histories
    MODIFY COLUMN from_status VARCHAR(32) NULL,
    MODIFY COLUMN to_status VARCHAR(32) NOT NULL;

ALTER TABLE invoice_items
    ADD COLUMN source_item_type VARCHAR(32) NULL AFTER reference_id,
    ADD COLUMN source_item_id BIGINT NULL AFTER source_item_type,
    ADD COLUMN refunded_amount BIGINT NOT NULL DEFAULT 0 AFTER total_amount,
    ADD COLUMN refunded_at DATETIME(6) NULL AFTER refunded_amount,
    ADD COLUMN refund_status VARCHAR(32) NULL AFTER refunded_at;

CREATE INDEX idx_invoice_items_source ON invoice_items(source_item_type, source_item_id);

ALTER TABLE service_order_items
    ADD COLUMN refund_reason VARCHAR(500) NULL AFTER cancelled_at,
    ADD COLUMN refunded_at DATETIME(6) NULL AFTER refund_reason,
    ADD COLUMN refunded_by_user_id BIGINT NULL AFTER refunded_at;

ALTER TABLE service_order_items
    ADD CONSTRAINT fk_service_order_items_refunded_by_user
        FOREIGN KEY (refunded_by_user_id) REFERENCES users(id);

ALTER TABLE prescription_items
    ADD COLUMN status VARCHAR(24) NULL AFTER instruction,
    ADD COLUMN refund_reason VARCHAR(500) NULL AFTER status,
    ADD COLUMN refunded_at DATETIME(6) NULL AFTER refund_reason,
    ADD COLUMN refunded_by_user_id BIGINT NULL AFTER refunded_at;

UPDATE prescription_items pi
JOIN prescriptions p ON p.id = pi.prescription_id
SET pi.status = CASE
    WHEN p.status = 'DISPENSED' THEN 'DISPENSED'
    WHEN p.status = 'PAID' THEN 'PAID'
    ELSE 'ISSUED'
END
WHERE pi.status IS NULL;

ALTER TABLE prescription_items
    MODIFY COLUMN status VARCHAR(24) NOT NULL;

ALTER TABLE prescription_items
    ADD CONSTRAINT fk_prescription_items_refunded_by_user
        FOREIGN KEY (refunded_by_user_id) REFERENCES users(id);

CREATE INDEX idx_prescription_items_status ON prescription_items(status);

CREATE TABLE IF NOT EXISTS refund_record_items (
    id BIGINT AUTO_INCREMENT NOT NULL,
    refund_record_id BIGINT NOT NULL,
    invoice_item_id BIGINT NOT NULL,
    source_item_type VARCHAR(32) NULL,
    source_item_id BIGINT NULL,
    name_snapshot VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    refund_amount BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_refund_record_items_refund_record
        FOREIGN KEY (refund_record_id) REFERENCES refund_records(id),
    CONSTRAINT fk_refund_record_items_invoice_item
        FOREIGN KEY (invoice_item_id) REFERENCES invoice_items(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_refund_record_items_record ON refund_record_items(refund_record_id);
CREATE INDEX idx_refund_record_items_invoice_item ON refund_record_items(invoice_item_id);
