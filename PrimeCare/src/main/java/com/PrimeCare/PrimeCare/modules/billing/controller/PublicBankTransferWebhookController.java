package com.PrimeCare.PrimeCare.modules.billing.controller;

import com.PrimeCare.PrimeCare.modules.billing.dto.response.InvoiceResponse;
import com.PrimeCare.PrimeCare.modules.billing.service.BankTransferWebhookVerifier;
import com.PrimeCare.PrimeCare.modules.billing.service.BillingService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/payments/bank-transfer")
@RequiredArgsConstructor
public class PublicBankTransferWebhookController {

    private static final String SIGNATURE_HEADER = "X-PrimeCare-Webhook-Signature";
    private static final String TIMESTAMP_HEADER = "X-PrimeCare-Webhook-Timestamp";

    private final BillingService billingService;
    private final BankTransferWebhookVerifier bankTransferWebhookVerifier;

    @PostMapping("/transactions")
    public ApiResponse<InvoiceResponse> receiveTransaction(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestBody(required = false) String payload
    ) {
        BankTransferWebhookVerifier.VerifiedBankTransferWebhook webhook =
                bankTransferWebhookVerifier.verify(payload, timestamp, signature);
        InvoiceResponse invoice = billingService.registerVerifiedBankTransferWebhookTransaction(webhook);
        return ApiResponse.ok("Đã tiếp nhận giao dịch ngân hàng", invoice);
    }
}
