ALTER TABLE invoices
    MODIFY service_order_id BIGINT NULL;

ALTER TABLE invoices
    ADD COLUMN prescription_id BIGINT NULL AFTER service_order_id;

CREATE UNIQUE INDEX uq_invoice_prescription ON invoices(prescription_id);

ALTER TABLE invoices
    ADD CONSTRAINT fk_invoices_prescription_id
        FOREIGN KEY (prescription_id) REFERENCES prescriptions(id);
