package com.PrimeCare.PrimeCare.modules.billing.controller;

import com.PrimeCare.PrimeCare.modules.billing.dto.response.InvoiceResponse;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.VnpayVerificationResult;
import com.PrimeCare.PrimeCare.modules.billing.service.BillingService;
import com.PrimeCare.PrimeCare.modules.billing.service.VnpayPaymentService;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/api/public/payments/vnpay")
@RequiredArgsConstructor
public class PublicVnpayController {

    private final VnpayPaymentService vnpayPaymentService;
    private final BillingService billingService;

    @Value("${app.vnpay.defaultFrontendReturnUrl:http://localhost:8081/app/cashier/invoices}")
    private String defaultFrontendReturnUrl = "http://localhost:8081/app/cashier/invoices";

    @Value("${app.vnpay.allowReturnConfirmation:true}")
    private boolean allowReturnConfirmation = true;

    @GetMapping("/return")
    public RedirectView handleReturn(@RequestParam MultiValueMap<String, String> params) {
        Map<String, String> requestParams = flatten(params);
        VnpayVerificationResult verification;
        try {
            verification = vnpayPaymentService.verifyCallback(requestParams);
        } catch (RuntimeException ex) {
            return redirectToFrontend(
                    resolveFrontendReturnUrl(requestParams),
                    "failed",
                    "invalid_signature",
                    requestParams.get("vnp_TxnRef"),
                    null,
                    null
            );
        }

        String frontendReturnUrl = resolveFrontendReturnUrl(requestParams);

        if (!verification.validSignature()) {
            return redirectToFrontend(
                    frontendReturnUrl,
                    "failed",
                    "invalid_signature",
                    verification.txnRef(),
                    null,
                    null
            );
        }

        if (!verification.isSuccess()) {
            InvoiceResponse invoice = resolveInvoiceByTxnRef(verification.txnRef());
            return redirectToFrontend(
                    frontendReturnUrl,
                    "failed",
                    verification.responseCode(),
                    verification.txnRef(),
                    invoice != null ? invoice.getId() : null,
                    null
            );
        }

        if (!allowReturnConfirmation) {
            InvoiceResponse invoice = resolveInvoiceByTxnRef(verification.txnRef());
            return redirectToFrontend(
                    frontendReturnUrl,
                    "success",
                    "00",
                    verification.txnRef(),
                    invoice != null ? invoice.getId() : null,
                    "pending"
            );
        }

        try {
            InvoiceResponse invoice = billingService.confirmVnpayPayment(
                    verification.txnRef(),
                    verification.amount(),
                    verification.transactionNo(),
                    verification.payDate()
            ).invoice();
            return redirectToFrontend(
                    frontendReturnUrl,
                    "success",
                    "00",
                    verification.txnRef(),
                    invoice.getId(),
                    null
            );
        } catch (ApiException ex) {
            InvoiceResponse invoice = resolveInvoiceByTxnRef(verification.txnRef());
            return redirectToFrontend(
                    frontendReturnUrl,
                    "failed",
                    ex.getErrorCode().name(),
                    verification.txnRef(),
                    invoice != null ? invoice.getId() : null,
                    null
            );
        } catch (RuntimeException ex) {
            InvoiceResponse invoice = resolveInvoiceByTxnRef(verification.txnRef());
            return redirectToFrontend(
                    frontendReturnUrl,
                    "failed",
                    "unknown_error",
                    verification.txnRef(),
                    invoice != null ? invoice.getId() : null,
                    null
            );
        }
    }

    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> handleIpn(@RequestParam MultiValueMap<String, String> params) {
        Map<String, String> requestParams = flatten(params);
        VnpayVerificationResult verification;
        try {
            verification = vnpayPaymentService.verifyCallback(requestParams);
        } catch (RuntimeException ex) {
            return ipnResponse("99", "Unknown error");
        }

        Map<String, String> response = new LinkedHashMap<>();

        if (!verification.validSignature()) {
            response.put("RspCode", "97");
            response.put("Message", "Invalid signature");
            return ResponseEntity.ok(response);
        }

        if (verification.txnRef() == null || verification.txnRef().isBlank()) {
            response.put("RspCode", "01");
            response.put("Message", "Order not found");
            return ResponseEntity.ok(response);
        }

        if (!verification.isSuccess()) {
            if (resolveInvoiceByTxnRef(verification.txnRef()) == null) {
                response.put("RspCode", "01");
                response.put("Message", "Order not found");
                return ResponseEntity.ok(response);
            }
            response.put("RspCode", "00");
            response.put("Message", "Payment failed acknowledged");
            return ResponseEntity.ok(response);
        }

        try {
            var result = billingService.confirmVnpayPayment(
                    verification.txnRef(),
                    verification.amount(),
                    verification.transactionNo(),
                    verification.payDate()
            );
            response.put("RspCode", result.alreadyPaid() ? "02" : "00");
            response.put("Message", result.alreadyPaid() ? "Order already confirmed" : "Confirm Success");
            return ResponseEntity.ok(response);
        } catch (ApiException ex) {
            response.put("RspCode", mapIpnErrorCode(ex));
            response.put("Message", mapIpnErrorMessage(ex));
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            response.put("RspCode", "99");
            response.put("Message", "Unknown error");
            return ResponseEntity.ok(response);
        }
    }

    private RedirectView redirectToFrontend(
            String frontendReturnUrl,
            String status,
            String code,
            String txnRef,
            Long invoiceId,
            String sync
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                frontendReturnUrl != null && !frontendReturnUrl.isBlank()
                        ? frontendReturnUrl
                        : defaultFrontendReturnUrl
        );
        addQueryParam(builder, "vnpayStatus", status);
        addQueryParam(builder, "vnpayCode", code);
        addQueryParam(builder, "vnpayTxnRef", txnRef);
        if (invoiceId != null) {
            builder.queryParam("invoiceId", invoiceId);
        }
        addQueryParam(builder, "sync", sync);
        String redirectUrl = builder.build()
                                    .encode()
                                    .toUriString();
        return new RedirectView(redirectUrl);
    }

    private void addQueryParam(UriComponentsBuilder builder, String key, String value) {
        if (value != null && !value.isBlank()) {
            builder.queryParam(key, value);
        }
    }

    private String resolveFrontendReturnUrl(Map<String, String> requestParams) {
        String frontendReturnUrl = requestParams.get("frontendReturnUrl");
        return frontendReturnUrl != null && !frontendReturnUrl.isBlank()
                ? frontendReturnUrl
                : defaultFrontendReturnUrl;
    }

    private InvoiceResponse resolveInvoiceByTxnRef(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) {
            return null;
        }
        try {
            return billingService.getInvoiceByTxnRef(txnRef);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String mapIpnErrorCode(ApiException ex) {
        if (ex.getErrorCode() == ErrorCode.INVOICE_NOT_FOUND) {
            return "01";
        }
        if (isVnpayAmountError(ex)) {
            return "04";
        }
        return "99";
    }

    private String mapIpnErrorMessage(ApiException ex) {
        String code = mapIpnErrorCode(ex);
        if ("01".equals(code)) {
            return "Order not found";
        }
        if ("04".equals(code)) {
            return "Invalid amount";
        }
        return "Unknown error";
    }

    private boolean isVnpayAmountError(ApiException ex) {
        if (ex.getErrorCode() != ErrorCode.INVOICE_INVALID_STATUS && ex.getErrorCode() != ErrorCode.INVALID_REQUEST) {
            return false;
        }
        String message = ex.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("số tiền");
    }

    private ResponseEntity<Map<String, String>> ipnResponse(String code, String message) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("RspCode", code);
        response.put("Message", message);
        return ResponseEntity.ok(response);
    }

    private Map<String, String> flatten(MultiValueMap<String, String> params) {
        Map<String, String> map = new LinkedHashMap<>();
        params.forEach((key, value) -> map.put(key, value != null && !value.isEmpty() ? value.get(0) : null));
        return map;
    }
}
