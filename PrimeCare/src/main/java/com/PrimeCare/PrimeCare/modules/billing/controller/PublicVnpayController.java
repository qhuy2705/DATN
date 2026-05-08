package com.PrimeCare.PrimeCare.modules.billing.controller;

import com.PrimeCare.PrimeCare.modules.billing.dto.response.InvoiceResponse;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.VnpayVerificationResult;
import com.PrimeCare.PrimeCare.modules.billing.service.BillingService;
import com.PrimeCare.PrimeCare.modules.billing.service.VnpayPaymentService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/api/public/payments/vnpay")
@RequiredArgsConstructor
public class PublicVnpayController {

    private final VnpayPaymentService vnpayPaymentService;
    private final BillingService billingService;

    @GetMapping("/return")
    public Object handleReturn(@RequestParam MultiValueMap<String, String> params) {
        Map<String, String> requestParams = flatten(params);
        VnpayVerificationResult verification = vnpayPaymentService.verifyCallback(requestParams);

        String resultCode = verification.responseCode();
        InvoiceResponse invoice = null;

        if (verification.validSignature() && verification.txnRef() != null) {
            try {
                if (verification.isSuccess()) {
                    invoice = billingService.confirmVnpayPayment(
                            verification.txnRef(),
                            verification.amount(),
                            verification.transactionNo(),
                            verification.payDate()
                    ).invoice();
                } else {
                    invoice = billingService.getInvoiceByTxnRef(verification.txnRef());
                }
            } catch (ApiException ex) {
                resultCode = ex.getErrorCode().name();
                invoice = null;
            } catch (RuntimeException ignored) {
                invoice = null;
            }
        }

        String frontendReturnUrl = verification.frontendReturnUrl() != null && !verification.frontendReturnUrl().isBlank()
                ? verification.frontendReturnUrl()
                : params.getFirst("frontendReturnUrl");

        if (frontendReturnUrl != null && !frontendReturnUrl.isBlank()) {
            String redirectUrl = UriComponentsBuilder.fromUriString(frontendReturnUrl)
                                                     .queryParam("vnpayStatus", verification.isSuccess() && invoice != null ? "success" : "failed")
                                                     .queryParam("vnpayCode", resultCode)
                                                     .queryParam("vnpayTxnRef", verification.txnRef())
                                                     .queryParam("invoiceId", invoice != null ? invoice.getId() : null)
                                                     .build(true)
                                                     .toUriString();
            return new RedirectView(redirectUrl);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("validSignature", verification.validSignature());
        body.put("success", verification.isSuccess() && invoice != null);
        body.put("responseCode", resultCode);
        body.put("txnRef", verification.txnRef());
        body.put("invoice", invoice);
        return ResponseEntity.ok(ApiResponse.ok("OK", body));
    }

    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> handleIpn(@RequestParam MultiValueMap<String, String> params) {
        Map<String, String> requestParams = flatten(params);
        VnpayVerificationResult verification = vnpayPaymentService.verifyCallback(requestParams);

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

        if (verification.isSuccess()) {
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
                response.put("RspCode", switch (ex.getErrorCode()) {
                    case INVOICE_INVALID_STATUS -> "04";
                    case INVOICE_NOT_FOUND -> "01";
                    default -> "99";
                });
                response.put("Message", ex.getMessage());
                return ResponseEntity.ok(response);
            } catch (RuntimeException ex) {
                response.put("RspCode", "99");
                response.put("Message", "Unknown error");
                return ResponseEntity.ok(response);
            }
        }

        response.put("RspCode", "00");
        response.put("Message", "Payment failed");
        return ResponseEntity.ok(response);
    }

    private Map<String, String> flatten(MultiValueMap<String, String> params) {
        Map<String, String> map = new LinkedHashMap<>();
        params.forEach((key, value) -> map.put(key, value != null && !value.isEmpty() ? value.get(0) : null));
        return map;
    }
}
