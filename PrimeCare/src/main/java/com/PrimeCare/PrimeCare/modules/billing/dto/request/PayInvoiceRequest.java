package com.PrimeCare.PrimeCare.modules.billing.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PayInvoiceRequest {
    @NotNull
    private PaymentMethod paymentMethod;
    private String returnUrl;
}
