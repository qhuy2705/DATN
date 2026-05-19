package com.PrimeCare.PrimeCare.modules.billing.controller;

import com.PrimeCare.PrimeCare.modules.billing.dto.response.InvoiceResponse;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.PaymentApplyResult;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.VnpayVerificationResult;
import com.PrimeCare.PrimeCare.modules.billing.service.BillingService;
import com.PrimeCare.PrimeCare.modules.billing.service.VnpayPaymentService;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicVnpayControllerTest {

    @Mock
    private VnpayPaymentService vnpayPaymentService;
    @Mock
    private BillingService billingService;

    private PublicVnpayController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicVnpayController(vnpayPaymentService, billingService);
    }

    @Test
    void ipnReturns97ForInvalidSignature() {
        when(vnpayPaymentService.verifyCallback(any())).thenReturn(verification(false, "TXN-1", 100L));

        Map<String, String> body = controller.handleIpn(params()).getBody();

        assertThat(body).containsEntry("RspCode", "97")
                        .containsEntry("Message", "Invalid signature");
        verifyNoInteractions(billingService);
    }

    @Test
    void ipnReturns01WhenInvoiceNotFound() {
        when(vnpayPaymentService.verifyCallback(any())).thenReturn(verification(true, "TXN-1", 100L));
        when(billingService.confirmVnpayPayment("TXN-1", 100L, "14123456", "20260102030405"))
                .thenThrow(new ApiException(ErrorCode.INVOICE_NOT_FOUND));

        Map<String, String> body = controller.handleIpn(params()).getBody();

        assertThat(body).containsEntry("RspCode", "01")
                        .containsEntry("Message", "Order not found");
    }

    @Test
    void ipnReturns04ForAmountMismatch() {
        when(vnpayPaymentService.verifyCallback(any())).thenReturn(verification(true, "TXN-1", 99L));
        when(billingService.confirmVnpayPayment("TXN-1", 99L, "14123456", "20260102030405"))
                .thenThrow(new ApiException(
                        ErrorCode.INVOICE_INVALID_STATUS,
                        "Số tiền thanh toán VNPAY không khớp hóa đơn"
                ));

        Map<String, String> body = controller.handleIpn(params()).getBody();

        assertThat(body).containsEntry("RspCode", "04")
                        .containsEntry("Message", "Invalid amount");
    }

    @Test
    void ipnReturns02WhenAlreadyPaid() {
        when(vnpayPaymentService.verifyCallback(any())).thenReturn(verification(true, "TXN-1", 100L));
        when(billingService.confirmVnpayPayment("TXN-1", 100L, "14123456", "20260102030405"))
                .thenReturn(new PaymentApplyResult(InvoiceResponse.builder().id(1L).build(), true));

        Map<String, String> body = controller.handleIpn(params()).getBody();

        assertThat(body).containsEntry("RspCode", "02")
                        .containsEntry("Message", "Order already confirmed");
    }

    @Test
    void ipnReturns00WhenPaymentConfirmed() {
        when(vnpayPaymentService.verifyCallback(any())).thenReturn(verification(true, "TXN-1", 100L));
        when(billingService.confirmVnpayPayment("TXN-1", 100L, "14123456", "20260102030405"))
                .thenReturn(new PaymentApplyResult(InvoiceResponse.builder().id(1L).build(), false));

        Map<String, String> body = controller.handleIpn(params()).getBody();

        assertThat(body).containsEntry("RspCode", "00")
                        .containsEntry("Message", "Confirm Success");
    }

    private VnpayVerificationResult verification(boolean validSignature, String txnRef, Long amount) {
        return new VnpayVerificationResult(
                validSignature,
                txnRef,
                "00",
                "00",
                "14123456",
                "20260102030405",
                null,
                amount,
                "Thanh toan hoa don INV-1"
        );
    }

    private MultiValueMap<String, String> params() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("vnp_TxnRef", "TXN-1");
        return params;
    }
}
