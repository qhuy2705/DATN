package com.PrimeCare.PrimeCare.modules.billing.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.PaymentMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeInvoicePaymentMethodRequest {

    @NotNull
    private PaymentMethod paymentMethod;

    private String returnUrl;

    @AssertTrue(message = "Payment method must be one of CASH, BANK_TRANSFER, VNPAY")
    public boolean isSupportedPaymentMethod() {
        return paymentMethod == null
                || paymentMethod == PaymentMethod.CASH
                || paymentMethod == PaymentMethod.BANK_TRANSFER
                || paymentMethod == PaymentMethod.VNPAY;
    }
}
