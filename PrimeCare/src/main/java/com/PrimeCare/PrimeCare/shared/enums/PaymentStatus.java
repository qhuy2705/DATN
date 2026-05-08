package com.PrimeCare.PrimeCare.shared.enums;

// Backend is the source of truth; frontend must use these exact payment statuses.
public enum PaymentStatus {
    UNPAID,
    PENDING_CONFIRMATION,
    PAYMENT_REVIEW,
    PAID,
    REFUNDED,
    VOID
}
