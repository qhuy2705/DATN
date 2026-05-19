package com.PrimeCare.PrimeCare.modules.billing.controller;

import com.PrimeCare.PrimeCare.modules.billing.dto.request.BankTransferTransactionRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.ChangeInvoicePaymentMethodRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.ManualBankTransferConfirmationRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.PayInvoiceRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.RefundInvoiceRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.RefundInvoiceItemsRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.CashierSummaryResponse;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.InvoicePdfJobResponse;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.InvoiceResponse;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.RefundableInvoiceItemsResponse;
import com.PrimeCare.PrimeCare.modules.billing.entity.InvoicePdfJob;
import com.PrimeCare.PrimeCare.modules.billing.service.BillingService;
import com.PrimeCare.PrimeCare.modules.billing.service.InvoicePdfJobService;
import com.PrimeCare.PrimeCare.modules.service_order.dto.response.CashierServiceOrderResponse;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.InvoicePdfJobStatus;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/cashier")
@RequiredArgsConstructor
public class CashierInvoiceController {

    private final BillingService billingService;
    private final InvoicePdfJobService invoicePdfJobService;

    @GetMapping("/service-orders")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<PageResponse<CashierServiceOrderResponse>> listServiceOrders(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) Boolean invoiced,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(
                "OK",
                billingService.listCashierServiceOrders(q, paymentStatus, invoiced, date != null ? date : fromDate, date != null ? date : toDate, pageable)
        );
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<PageResponse<InvoiceResponse>> listInvoices(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", billingService.listInvoices(q, paymentStatus, date != null ? date : fromDate, date != null ? date : toDate, pageable));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<CashierSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return ApiResponse.ok("OK", billingService.summary(date != null ? date : fromDate, date != null ? date : toDate));
    }

    @GetMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<InvoiceResponse> getInvoice(@PathVariable Long invoiceId) {
        return ApiResponse.ok("OK", billingService.getInvoice(invoiceId));
    }

    @PostMapping("/service-orders/{serviceOrderId}/invoice")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<InvoiceResponse> createInvoice(
            @PathVariable Long serviceOrderId,
            Authentication authentication,
            @Valid @RequestBody PayInvoiceRequest req
    ) {
        return ApiResponse.ok(
                "Tạo hóa đơn thành công",
                billingService.createInvoice(serviceOrderId, Long.valueOf(authentication.getName()), req)
        );
    }

    @PostMapping("/prescriptions/{prescriptionId}/invoice")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<InvoiceResponse> createPrescriptionInvoice(
            @PathVariable Long prescriptionId,
            Authentication authentication,
            @Valid @RequestBody PayInvoiceRequest req
    ) {
        return ApiResponse.ok(
                "Tạo hóa đơn thuốc thành công",
                billingService.createPrescriptionInvoice(prescriptionId, Long.valueOf(authentication.getName()), req)
        );
    }

    @PostMapping("/invoices/{invoiceId}/mark-paid")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<InvoiceResponse> markPaid(
            @PathVariable Long invoiceId,
            Authentication authentication
    ) {
        Long cashierUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("Đã xác nhận thanh toán", billingService.markPaid(invoiceId, cashierUserId));
    }

    @PostMapping("/invoices/{invoiceId}/change-payment-method")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<InvoiceResponse> changePaymentMethod(
            @PathVariable Long invoiceId,
            Authentication authentication,
            @Valid @RequestBody ChangeInvoicePaymentMethodRequest req
    ) {
        Long cashierUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Đã cập nhật phương thức thanh toán",
                billingService.changeInvoicePaymentMethod(invoiceId, cashierUserId, req)
        );
    }

    @PostMapping("/invoices/{invoiceId}/bank-transfer/reconcile")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<InvoiceResponse> reconcileBankTransfer(
            @PathVariable Long invoiceId,
            @Valid @RequestBody BankTransferTransactionRequest req,
            Authentication authentication
    ) {
        Long cashierUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Đã tiếp nhận giao dịch chuyển khoản",
                billingService.registerBankTransferTransaction(invoiceId, req, cashierUserId)
        );
    }

    @PostMapping("/invoices/{invoiceId}/bank-transfer/confirm")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<InvoiceResponse> confirmBankTransfer(
            @PathVariable Long invoiceId,
            @RequestBody(required = false) ManualBankTransferConfirmationRequest req,
            Authentication authentication
    ) {
        Long cashierUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Đã xác nhận chuyển khoản thủ công",
                billingService.confirmBankTransferReview(
                        invoiceId,
                        req != null ? req.getTransactionRef() : null,
                        req != null ? req.getNote() : null,
                        cashierUserId
                )
        );
    }

    @PostMapping("/invoices/{invoiceId}/refund")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<InvoiceResponse> refundInvoice(
            @PathVariable Long invoiceId,
            @RequestParam(required = false) Long refundAmount,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) RefundInvoiceRequest request,
            Authentication authentication
    ) {
        Long cashierUserId = Long.valueOf(authentication.getName());
        Long effectiveRefundAmount = refundAmount != null
                ? refundAmount
                : (request != null ? request.getRefundAmount() : null);
        String effectiveReason = reason != null && !reason.isBlank()
                ? reason
                : (request != null ? request.getReason() : null);
        return ApiResponse.ok("Đã hoàn tiền", billingService.refundInvoice(invoiceId, effectiveRefundAmount, effectiveReason, cashierUserId));
    }

    @GetMapping("/invoices/{invoiceId}/refundable-items")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<RefundableInvoiceItemsResponse> refundableItems(@PathVariable Long invoiceId) {
        return ApiResponse.ok("OK", billingService.getRefundableItems(invoiceId));
    }

    @PostMapping("/invoices/{invoiceId}/refund-items")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<InvoiceResponse> refundItems(
            @PathVariable Long invoiceId,
            @Valid @RequestBody RefundInvoiceItemsRequest request,
            Authentication authentication
    ) {
        Long cashierUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("Đã hoàn tiền các mục đã chọn", billingService.refundInvoiceItems(invoiceId, request, cashierUserId));
    }

    @PostMapping("/invoices/{invoiceId}/pdf-jobs")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<InvoicePdfJobResponse> requestPdf(
            @PathVariable Long invoiceId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Long cashierUserId = Long.valueOf(authentication.getName());
        InvoicePdfJob job = invoicePdfJobService.requestGenerate(invoiceId, cashierUserId);
        return ApiResponse.ok("Đã tạo job sinh PDF", toResponse(job, request));
    }

    @GetMapping("/invoice-pdf-jobs/{jobId}")
    @PreAuthorize("hasRole('CASHIER')")
    public ApiResponse<InvoicePdfJobResponse> getPdfJob(
            @PathVariable Long jobId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Long cashierUserId = Long.valueOf(authentication.getName());
        InvoicePdfJob job = invoicePdfJobService.getJob(jobId, cashierUserId);
        return ApiResponse.ok("OK", toResponse(job, request));
    }

    @GetMapping("/invoice-pdf-jobs/{jobId}/download")
    @PreAuthorize("hasRole('CASHIER')")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable Long jobId,
            Authentication authentication
    ) {
        Long cashierUserId = Long.valueOf(authentication.getName());
        byte[] pdf = invoicePdfJobService.download(jobId, cashierUserId);
        InvoicePdfJob job = invoicePdfJobService.getJob(jobId, cashierUserId);

        return ResponseEntity.ok()
                             .contentType(MediaType.APPLICATION_PDF)
                             .header(
                                     HttpHeaders.CONTENT_DISPOSITION,
                                     ContentDisposition.attachment()
                                                       .filename("invoice-" + job.getInvoiceId() + ".pdf")
                                                       .build()
                                                       .toString()
                             )
                             .body(pdf);
    }

    private InvoicePdfJobResponse toResponse(InvoicePdfJob job, HttpServletRequest request) {
        String downloadUrl = job.getStatus() == InvoicePdfJobStatus.COMPLETED
                ? "/api/cashier/invoice-pdf-jobs/" + job.getId() + "/download"
                : null;

        return InvoicePdfJobResponse.builder()
                                    .id(job.getId())
                                    .invoiceId(job.getInvoiceId())
                                    .status(job.getStatus())
                                    .errorMessage(job.getErrorMessage())
                                    .downloadUrl(downloadUrl)
                                    .build();
    }
}
