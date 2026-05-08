package com.PrimeCare.PrimeCare.modules.billing.messaging;

public record InvoicePdfRequestedMessage(Long jobId, Long invoiceId) {
}
