package com.PrimeCare.PrimeCare.modules.billing.dto.response;

public record VnpayPaymentInit(String txnRef, String paymentUrl, String returnUrl) {
}
