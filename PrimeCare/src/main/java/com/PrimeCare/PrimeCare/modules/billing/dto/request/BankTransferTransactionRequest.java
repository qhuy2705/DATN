package com.PrimeCare.PrimeCare.modules.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BankTransferTransactionRequest {
    @NotBlank
    private String transactionRef;

    @NotNull
    private Long amount;

    private String provider;
    private String transferContent;
    private String bankAccountNo;
    private String bankCode;
    private LocalDateTime transactionTime;
    private String rawPayload;
}
