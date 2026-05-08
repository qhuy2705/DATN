package com.PrimeCare.PrimeCare.modules.billing.dto.request;

import lombok.Data;

@Data
public class ManualBankTransferConfirmationRequest {
    private String transactionRef;
    private String note;
}
