package com.PrimeCare.PrimeCare.modules.billing.dto.response;

public record PaymentApplyResult(InvoiceResponse invoice, boolean alreadyPaid) {
}
