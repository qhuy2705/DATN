package com.PrimeCare.PrimeCare.modules.billing.dto.response;

public record VnpayVerificationResult(
        boolean validSignature,
        String txnRef,
        String responseCode,
        String transactionStatus,
        String transactionNo,
        String payDate,
        String frontendReturnUrl,
        Long amount,
        String orderInfo
) {
    public boolean isSuccess() {
        return validSignature
                && "00".equals(responseCode)
                && (transactionStatus == null || transactionStatus.isBlank() || "00".equals(transactionStatus));
    }
}
