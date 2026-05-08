package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.BankTransferTransactionRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.PayInvoiceRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.CashierSummaryResponse;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.InvoiceResponse;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.PaymentApplyResult;
import com.PrimeCare.PrimeCare.modules.billing.entity.BankTransaction;
import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.billing.entity.InvoiceItem;
import com.PrimeCare.PrimeCare.modules.billing.entity.PaymentIntent;
import com.PrimeCare.PrimeCare.modules.billing.entity.RefundRecord;
import com.PrimeCare.PrimeCare.modules.billing.repository.BankTransactionRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.PaymentIntentRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.RefundRecordRepository;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterWorkflowService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.dto.response.CashierServiceOrderResponse;
import com.PrimeCare.PrimeCare.modules.service_order.dto.response.ServiceOrderItemResponse;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.modules.service_order.service.DepartmentQueueAllocator;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BankTransactionStatus;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.PaymentIntentProvider;
import com.PrimeCare.PrimeCare.shared.enums.PaymentIntentStatus;
import com.PrimeCare.PrimeCare.shared.enums.PaymentMethod;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final ServiceOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final VnpayPaymentService vnpayPaymentService;
    private final BillingQrService billingQrService;
    private final PaymentIntentRepository paymentIntentRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final DepartmentQueueAllocator departmentQueueAllocator;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final AfterCommitExecutor afterCommitExecutor;
    private final AuditLogService auditLogService;
    private final InvoiceStatusHistoryService invoiceStatusHistoryService;
    private final RefundRecordRepository refundRecordRepository;
    private final EncounterWorkflowService encounterWorkflowService;
    private final InternalNotificationService internalNotificationService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public PageResponse<CashierServiceOrderResponse> listCashierServiceOrders(
            String q,
            PaymentStatus paymentStatus,
            Boolean invoiced,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        long started = System.nanoTime();
        String keyword = optionalText(q, null);
        Page<ServiceOrder> page = orderRepository.searchCashierOrders(
                keyword,
                keyword != null ? keyword.toLowerCase(Locale.ROOT) : null,
                paymentStatus,
                invoiced,
                startOfDay(fromDate),
                dayAfterStart(toDate),
                pageable
        );

        List<Long> serviceOrderIds = page.getContent().stream().map(ServiceOrder::getId).toList();
        Map<Long, Invoice> invoiceByServiceOrderId = serviceOrderIds.isEmpty()
                ? Map.of()
                : invoiceRepository.findByServiceOrder_IdIn(serviceOrderIds)
                                   .stream()
                                   .collect(Collectors.toMap(invoice -> invoice.getServiceOrder().getId(), Function.identity()));

        PageResponse<CashierServiceOrderResponse> response = PageResponse.<CashierServiceOrderResponse>builder()
                           .items(page.getContent().stream()
                                      .map(order -> toCashierServiceOrderResponse(order, invoiceByServiceOrderId.get(order.getId())))
                                      .toList())
                           .meta(toMeta(page, pageable))
                           .build();
        log.info("cashier service-orders durationMs={} paymentStatus={} invoiced={} size={}",
                durationMs(started), paymentStatus, invoiced, page.getNumberOfElements());
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> listInvoices(
            String q,
            PaymentStatus paymentStatus,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        long started = System.nanoTime();
        String keyword = optionalText(q, null);
        Page<Long> page = invoiceRepository.findIdsForCashier(
                keyword,
                keyword != null ? keyword.toLowerCase(Locale.ROOT) : null,
                paymentStatus,
                startOfDay(fromDate),
                dayAfterStart(toDate),
                pageable
        );

        Map<Long, Invoice> invoiceById = new LinkedHashMap<>();
        if (!page.getContent().isEmpty()) {
            invoiceRepository.findAllWithListDetailsByIdIn(page.getContent())
                             .forEach(invoice -> invoiceById.put(invoice.getId(), invoice));
        }

        PageResponse<InvoiceResponse> response = PageResponse.<InvoiceResponse>builder()
                           .items(page.getContent().stream()
                                      .map(invoiceById::get)
                                      .filter(java.util.Objects::nonNull)
                                      .map(invoice -> toResponse(invoice, false))
                                      .toList())
                           .meta(toMeta(page, pageable))
                           .build();
        log.info("cashier invoices durationMs={} paymentStatus={} size={}",
                durationMs(started), paymentStatus, page.getNumberOfElements());
        return response;
    }

    @Transactional(readOnly = true)
    public CashierSummaryResponse summary(LocalDate fromDate, LocalDate toDate) {
        long started = System.nanoTime();
        LocalDateTime fromTime = startOfDay(fromDate);
        LocalDateTime toTime = dayAfterStart(toDate);
        var invoiceSummary = invoiceRepository.summarizeCashierInvoices(fromTime, toTime);
        var orderSummary = orderRepository.summarizeCashierServiceOrders(fromTime, toTime);

        CashierSummaryResponse response = CashierSummaryResponse.builder()
                .invoiceCount(invoiceSummary != null ? invoiceSummary.getInvoiceCount() : 0)
                .unpaidInvoiceCount(invoiceSummary != null ? invoiceSummary.getUnpaidInvoiceCount() : 0)
                .pendingConfirmationInvoiceCount(invoiceSummary != null ? invoiceSummary.getPendingConfirmationInvoiceCount() : 0)
                .paymentReviewInvoiceCount(invoiceSummary != null ? invoiceSummary.getPaymentReviewInvoiceCount() : 0)
                .paidInvoiceCount(invoiceSummary != null ? invoiceSummary.getPaidInvoiceCount() : 0)
                .refundedInvoiceCount(invoiceSummary != null ? invoiceSummary.getRefundedInvoiceCount() : 0)
                .paidRevenue(invoiceSummary != null ? invoiceSummary.getPaidRevenue() : 0)
                .serviceOrderCount(orderSummary != null ? orderSummary.getServiceOrderCount() : 0)
                .uninvoicedServiceOrderCount(orderSummary != null ? orderSummary.getUninvoicedServiceOrderCount() : 0)
                .unpaidServiceOrderCount(orderSummary != null ? orderSummary.getUnpaidServiceOrderCount() : 0)
                .build();
        log.info("cashier summary durationMs={}", durationMs(started));
        return response;
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                                           .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
        return toResponse(invoice, true);
    }

    @Transactional
    public InvoiceResponse createInvoice(Long serviceOrderId, Long cashierUserId, PayInvoiceRequest req) {
        ServiceOrder order = orderRepository.findWithLockById(serviceOrderId)
                                            .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_ORDER_NOT_FOUND));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Phiếu chỉ định đã được thanh toán");
        }

        if (invoiceRepository.existsByServiceOrder_Id(order.getId())) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Phiếu chỉ định đã có hóa đơn");
        }

        User cashier = userRepository.findById(cashierUserId)
                                     .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        long totalSubtotal = 0L;
        long totalTax = 0L;
        List<InvoiceItem> invoiceItems = new ArrayList<>();

        for (ServiceOrderItem orderItem : order.getItems()) {
            long itemSubtotal = orderItem.getPriceSnapshot() * orderItem.getQuantity();
            BigDecimal taxRate = new BigDecimal("0.10");
            long itemTax = new BigDecimal(itemSubtotal).multiply(taxRate).longValue();
            long itemTotal = itemSubtotal + itemTax;

            totalSubtotal += itemSubtotal;
            totalTax += itemTax;

            InvoiceItem invoiceItem = InvoiceItem.builder()
                    .referenceType(InvoiceItem.ReferenceType.CLINICAL_SERVICE)
                    .referenceId(orderItem.getMedicalService().getId())
                    .nameSnapshot(orderItem.getServiceNameVnSnapshot())
                    .unitPrice(orderItem.getPriceSnapshot())
                    .quantity(orderItem.getQuantity())
                    .taxRate(taxRate)
                    .subtotalAmount(itemSubtotal)
                    .taxAmount(itemTax)
                    .totalAmount(itemTotal)
                    .build();

            invoiceItems.add(invoiceItem);
        }

        long finalTotal = totalSubtotal + totalTax;
        PaymentStatus initialPaymentStatus = req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                ? PaymentStatus.PENDING_CONFIRMATION
                : PaymentStatus.UNPAID;

        Invoice invoice = Invoice.builder()
                                 .code(generateInvoiceCode(order))
                                 .serviceOrder(order)
                                 .cashier(cashier)
                                 .subtotalAmount(totalSubtotal)
                                 .discountAmount(0L)
                                 .taxAmount(totalTax)
                                 .totalAmount(finalTotal)
                                 .paymentMethod(req.getPaymentMethod())
                                 .paymentStatus(initialPaymentStatus)
                                 .paymentReference(req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER ? generatePaymentReference() : null)
                                 .items(new ArrayList<>())
                                 .build();

        if (req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            invoice.setTransferContent(billingQrService.buildPaymentContent(invoice));
        }

        for (InvoiceItem item : invoiceItems) {
            item.setInvoice(invoice);
            invoice.getItems().add(item);
        }

        if (req.getPaymentMethod() == PaymentMethod.VNPAY) {
            var init = vnpayPaymentService.init(invoice.getCode(), invoice.getTotalAmount(), req.getReturnUrl());
            invoice.setVnpTxnRef(init.txnRef());
            invoice.setVnpPaymentUrl(init.paymentUrl());
        }

        Invoice saved = invoiceRepository.save(invoice);

        if (saved.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            upsertPaymentIntent(saved, null, PaymentIntentStatus.PENDING, null, null);
        }

        invoiceStatusHistoryService.record(saved, null, saved.getPaymentStatus(), cashier, "Invoice created");
        auditLogService.log(cashier, "CREATE", "INVOICE", saved.getId(), null, snapshotInvoice(saved));
        publishInvoiceCreatedRealtime(saved);

        return toResponse(saved, true);
    }

    @Transactional
    public InvoiceResponse markPaid(Long invoiceId, Long cashierUserId) {
        Invoice invoice = invoiceRepository.findWithLockById(invoiceId)
                                           .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));

        User cashier = userRepository.findById(cashierUserId)
                                     .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
            return toResponse(invoice, true);
        }

        Map<String, Object> before = snapshotInvoice(invoice);
        var previousPaymentStatus = invoice.getPaymentStatus();

        applyPaidState(invoice, LocalDateTime.now());
        invoice.setPaymentReviewReason(null);

        Invoice saved = invoiceRepository.save(invoice);
        invoiceStatusHistoryService.record(saved, previousPaymentStatus, saved.getPaymentStatus(), cashier, "Cashier marked invoice as paid");

        if (saved.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            upsertPaymentIntent(saved, null, PaymentIntentStatus.CONFIRMED, LocalDateTime.now(), null);
        }

        auditLogService.log(cashier, "MARK_PAID", "INVOICE", saved.getId(), before, snapshotInvoice(saved));
        publishPaidRealtime(saved);

        return toResponse(saved, true);
    }

    @Transactional
    public InvoiceResponse markPaidFromVnpay(String txnRef) {
        return confirmVnpayPayment(txnRef, null, null, null).invoice();
    }

    @Transactional
    public PaymentApplyResult confirmVnpayPayment(String txnRef, Long paidAmount, String transactionNo, String payDate) {
        Invoice invoice = invoiceRepository.findWithLockByVnpTxnRef(txnRef)
                                           .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));

        if (invoice.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Hóa đơn này không sử dụng VNPAY");
        }

        if (paidAmount != null && invoice.getTotalAmount() != null && !invoice.getTotalAmount().equals(paidAmount)) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Số tiền thanh toán VNPAY không khớp hóa đơn");
        }

        if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
            return new PaymentApplyResult(toResponse(invoice, true), true);
        }

        Map<String, Object> before = snapshotInvoice(invoice);
        var previousPaymentStatus = invoice.getPaymentStatus();
        applyPaidState(invoice, LocalDateTime.now());
        enrichVnpayNote(invoice, transactionNo, payDate);
        Invoice saved = invoiceRepository.save(invoice);
        invoiceStatusHistoryService.record(saved, previousPaymentStatus, saved.getPaymentStatus(), null, "VNPay payment confirmed");

        auditLogService.log(null, "MARK_PAID_VNPAY", "INVOICE", saved.getId(), before, snapshotInvoice(saved));
        publishPaidRealtime(saved);

        return new PaymentApplyResult(toResponse(saved, true), false);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByTxnRef(String txnRef) {
        Invoice invoice = invoiceRepository.findByVnpTxnRef(txnRef)
                                           .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
        return toResponse(invoice, true);
    }

    @Transactional
    public InvoiceResponse registerBankTransferTransaction(Long invoiceId, BankTransferTransactionRequest req, Long cashierUserId) {
        return registerBankTransferTransactionInternal(invoiceId, req, cashierUserId, false);
    }

    @Transactional
    public InvoiceResponse registerVerifiedBankTransferWebhookTransaction(
            BankTransferWebhookVerifier.VerifiedBankTransferWebhook webhook
    ) {
        if (webhook == null || webhook.transaction() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Webhook ngân hàng chưa được xác thực");
        }
        return registerBankTransferTransactionInternal(null, webhook.transaction(), null, true);
    }

    private InvoiceResponse registerBankTransferTransactionInternal(
            Long invoiceId,
            BankTransferTransactionRequest req,
            Long cashierUserId,
            boolean verifiedWebhook
    ) {
        if (invoiceId == null && cashierUserId == null && !verifiedWebhook) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Webhook ngân hàng chưa được xác thực");
        }

        if (req.getAmount() == null || req.getAmount() <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Số tiền giao dịch không hợp lệ");
        }

        Optional<BankTransaction> existing = bankTransactionRepository.findByTransactionRef(req.getTransactionRef().trim());
        if (existing.isPresent()) {
            Invoice matchedInvoice = existing.get().getMatchedInvoice();
            if (matchedInvoice != null) {
                return toResponse(matchedInvoice, true);
            }
            if (invoiceId != null) {
                Invoice invoice = invoiceRepository.findById(invoiceId)
                        .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
                return toResponse(invoice, true);
            }
            return null;
        }

        LocalDateTime detectedAt = req.getTransactionTime() != null ? req.getTransactionTime() : LocalDateTime.now();
        BankTransaction transaction = BankTransaction.builder()
                .provider(optionalText(req.getProvider(), "MANUAL_ENTRY"))
                .transactionRef(req.getTransactionRef().trim())
                .amount(req.getAmount())
                .transferContent(optionalText(req.getTransferContent(), null))
                .bankAccountNo(optionalText(req.getBankAccountNo(), null))
                .bankCode(optionalText(req.getBankCode(), null))
                .transactionTime(detectedAt)
                .status(BankTransactionStatus.RECEIVED)
                .rawPayload(req.getRawPayload())
                .build();
        bankTransactionRepository.save(transaction);

        List<PaymentIntent> candidates = resolveCandidateIntents(invoiceId, req.getAmount(), req.getTransferContent());
        if (candidates.size() == 1) {
            PaymentIntent intent = candidates.get(0);
            Invoice invoice = invoiceRepository.findWithLockById(intent.getInvoice().getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
            String note = verifiedWebhook ? "Verified bank transfer webhook" : "Auto matched bank transfer";
            return finalizeMatchedBankTransfer(invoice, transaction, detectedAt, cashierUserId, note);
        }

        transaction.setStatus(BankTransactionStatus.REVIEW);
        transaction.setReviewReason(candidates.isEmpty()
                ? "Không tìm thấy hóa đơn phù hợp"
                : "Có nhiều hóa đơn phù hợp, cần cashier xác nhận");
        bankTransactionRepository.save(transaction);

        if (invoiceId != null) {
            flagInvoiceForReview(invoiceId, cashierUserId, transaction.getReviewReason());
            Invoice invoice = invoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
            return toResponse(invoice, true);
        }
        return null;
    }

    @Transactional
    public InvoiceResponse confirmBankTransferReview(Long invoiceId, String transactionRef, String note, Long cashierUserId) {
        Invoice invoice = invoiceRepository.findWithLockById(invoiceId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));

        if (invoice.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Hóa đơn này không phải chuyển khoản");
        }

        User cashier = userRepository.findById(cashierUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        BankTransaction transaction = null;
        if (transactionRef != null && !transactionRef.isBlank()) {
            transaction = bankTransactionRepository.findByTransactionRef(transactionRef.trim())
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Không tìm thấy giao dịch ngân hàng"));
        }

        Map<String, Object> before = snapshotInvoice(invoice);
        PaymentStatus previousStatus = invoice.getPaymentStatus();
        LocalDateTime paidTime = transaction != null && transaction.getTransactionTime() != null
                ? transaction.getTransactionTime()
                : LocalDateTime.now();

        applyPaidState(invoice, paidTime);
        invoice.setPaymentDetectedAt(paidTime);
        invoice.setPaymentReviewReason(null);
        appendNote(invoice, note != null && !note.isBlank() ? note : "Cashier confirmed bank transfer manually");

        Invoice saved = invoiceRepository.save(invoice);
        upsertPaymentIntent(saved, transactionRef, PaymentIntentStatus.CONFIRMED, paidTime, null);

        if (transaction != null) {
            transaction.setStatus(BankTransactionStatus.MATCHED);
            transaction.setMatchedInvoice(saved);
            transaction.setMatchedAt(LocalDateTime.now());
            transaction.setReviewReason(null);
            bankTransactionRepository.save(transaction);
        }

        invoiceStatusHistoryService.record(saved, previousStatus, saved.getPaymentStatus(), cashier, "Manual bank transfer confirmation");
        auditLogService.log(cashier, "CONFIRM_BANK_TRANSFER", "INVOICE", saved.getId(), before, snapshotInvoice(saved));
        publishPaidRealtime(saved);
        return toResponse(saved, true);
    }

    private List<PaymentIntent> resolveCandidateIntents(Long invoiceId, Long amount, String transferContent) {
        if (invoiceId != null) {
            PaymentIntent lockedIntent = paymentIntentRepository.findWithLockByInvoice_Id(invoiceId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
            return List.of(lockedIntent);
        }

        String normalizedContent = normalizeText(transferContent);
        List<PaymentIntent> candidates = paymentIntentRepository.findByExpectedAmountAndStatusIn(
                amount,
                List.of(PaymentIntentStatus.PENDING, PaymentIntentStatus.REVIEW)
        );

        if (normalizedContent.isBlank()) {
            return candidates.size() == 1 ? candidates : List.of();
        }

        return candidates.stream()
                .filter(intent -> {
                    String paymentReference = normalizeText(intent.getPaymentReference());
                    String expectedContent = normalizeText(intent.getTransferContent());
                    return (!paymentReference.isBlank() && normalizedContent.contains(paymentReference))
                            || (!expectedContent.isBlank() && normalizedContent.contains(expectedContent));
                })
                .toList();
    }

    private InvoiceResponse finalizeMatchedBankTransfer(
            Invoice invoice,
            BankTransaction transaction,
            LocalDateTime detectedAt,
            Long cashierUserId,
            String note
    ) {
        if (invoice.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Hóa đơn này không phải chuyển khoản");
        }
        if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
            return toResponse(invoice, true);
        }

        User cashier = null;
        if (cashierUserId != null) {
            cashier = userRepository.findById(cashierUserId)
                    .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        }

        Map<String, Object> before = snapshotInvoice(invoice);
        PaymentStatus previousStatus = invoice.getPaymentStatus();

        invoice.setPaymentDetectedAt(detectedAt);
        invoice.setPaymentReviewReason(null);
        applyPaidState(invoice, detectedAt);
        appendNote(invoice, note + " [txnRef=" + transaction.getTransactionRef() + "]");

        Invoice saved = invoiceRepository.save(invoice);
        upsertPaymentIntent(saved, transaction.getTransactionRef(), PaymentIntentStatus.CONFIRMED, detectedAt, null);

        transaction.setStatus(BankTransactionStatus.MATCHED);
        transaction.setMatchedInvoice(saved);
        transaction.setMatchedAt(LocalDateTime.now());
        transaction.setReviewReason(null);
        bankTransactionRepository.save(transaction);

        invoiceStatusHistoryService.record(saved, previousStatus, saved.getPaymentStatus(), cashier, "Bank transfer reconciled");
        auditLogService.log(cashier, "RECONCILE_BANK_TRANSFER", "INVOICE", saved.getId(), before, snapshotInvoice(saved));
        publishPaidRealtime(saved);
        return toResponse(saved, true);
    }

    private void flagInvoiceForReview(Long invoiceId, Long cashierUserId, String reason) {
        Invoice invoice = invoiceRepository.findWithLockById(invoiceId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
        if (invoice.getPaymentStatus() == PaymentStatus.PAID || invoice.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            return;
        }

        User cashier = null;
        if (cashierUserId != null) {
            cashier = userRepository.findById(cashierUserId)
                    .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        }

        Map<String, Object> before = snapshotInvoice(invoice);
        PaymentStatus previousStatus = invoice.getPaymentStatus();
        invoice.setPaymentStatus(PaymentStatus.PAYMENT_REVIEW);
        invoice.setPaymentReviewReason(reason);
        Invoice saved = invoiceRepository.save(invoice);
        upsertPaymentIntent(saved, null, PaymentIntentStatus.REVIEW, null, reason);
        invoiceStatusHistoryService.record(saved, previousStatus, saved.getPaymentStatus(), cashier, reason);
        auditLogService.log(cashier, "BANK_TRANSFER_REVIEW", "INVOICE", saved.getId(), before, snapshotInvoice(saved));
        notifyPaymentReview(saved, reason);
    }

    private void notifyPaymentReview(Invoice invoice, String reason) {
        String message = "Hóa đơn " + invoice.getCode() + " cần kiểm tra thanh toán"
                + (reason != null && !reason.isBlank() ? ": " + reason : ".");
        internalNotificationService.notifyRole(
                UserRole.CASHIER,
                "PAYMENT_REVIEW_REQUIRED",
                InternalNotificationService.SEVERITY_WARNING,
                "Thanh toán cần kiểm tra",
                message,
                "/app/cashier/invoices",
                "INVOICE",
                invoice.getId()
        );
        internalNotificationService.notifyRole(
                UserRole.OPERATIONS_ADMIN,
                "PAYMENT_REVIEW_REQUIRED",
                InternalNotificationService.SEVERITY_WARNING,
                "Webhook thanh toán cần kiểm tra",
                message,
                "/app/cashier/invoices",
                "INVOICE",
                invoice.getId()
        );
    }

    private void upsertPaymentIntent(
            Invoice invoice,
            String matchedTransactionRef,
            PaymentIntentStatus status,
            LocalDateTime detectedOrConfirmedAt,
            String reviewReason
    ) {
        if (invoice == null || invoice.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            return;
        }
        PaymentIntent intent = paymentIntentRepository.findByInvoice_Id(invoice.getId())
                .orElseGet(() -> PaymentIntent.builder()
                        .invoice(invoice)
                        .provider(PaymentIntentProvider.VIETQR)
                        .paymentReference(invoice.getPaymentReference())
                        .build());

        intent.setTransferContent(invoice.getTransferContent());
        intent.setBankCode(optionalText(billingQrService.bankCode(), null));
        intent.setBankAccountNo(optionalText(billingQrService.accountNo(), null));
        intent.setBankAccountName(optionalText(billingQrService.accountName(), null));
        intent.setExpectedAmount(invoice.getTotalAmount());
        intent.setQrPayload(billingQrService.buildVietQrPayload(invoice));
        intent.setStatus(status);
        intent.setReviewReason(reviewReason);
        intent.setExpiresAt(invoice.getCreatedAt() != null ? invoice.getCreatedAt().plusDays(1) : LocalDateTime.now().plusDays(1));
        if (matchedTransactionRef != null && !matchedTransactionRef.isBlank()) {
            intent.setMatchedTransactionRef(matchedTransactionRef);
        }
        if (status == PaymentIntentStatus.DETECTED) {
            intent.setDetectedAt(detectedOrConfirmedAt != null ? detectedOrConfirmedAt : LocalDateTime.now());
        }
        if (status == PaymentIntentStatus.CONFIRMED) {
            LocalDateTime ts = detectedOrConfirmedAt != null ? detectedOrConfirmedAt : LocalDateTime.now();
            intent.setDetectedAt(ts);
            intent.setConfirmedAt(ts);
        }
        paymentIntentRepository.save(intent);
    }

    private void applyPaidState(Invoice invoice, LocalDateTime paidAt) {
        ServiceOrder order = orderRepository.findWithLockById(invoice.getServiceOrder().getId())
                                            .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_ORDER_NOT_FOUND));

        LocalDateTime now = paidAt != null ? paidAt : LocalDateTime.now();
        LocalDate queueDate = now.toLocalDate();

        invoice.setPaymentStatus(PaymentStatus.PAID);
        invoice.setPaidAt(now);

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(ServiceOrderStatus.PAID);
        order.setPaidAt(now);

        for (ServiceOrderItem item : order.getItems()) {
            item.setStatus(ServiceOrderItemStatus.WAITING_EXECUTION);
            item.setQueuedAt(now);
            item.setQueueNo(departmentQueueAllocator.allocateNext(item.getAssignedDepartmentCode(), queueDate));
        }

        encounterWorkflowService.refreshStatus(order.getEncounter());
    }

    private void publishPaidRealtime(Invoice invoice) {
        ServiceOrder order = invoice.getServiceOrder();
        var departmentCodes = order.getItems().stream()
                                   .map(ServiceOrderItem::getAssignedDepartmentCode)
                                   .filter(code -> code != null && !code.isBlank())
                                   .distinct()
                                   .toList();

        Long doctorProfileId = order.getEncounter().getDoctor().getId();
        Long encounterId = order.getEncounter().getId();
        Long serviceOrderId = order.getId();
        String serviceOrderCode = order.getCode();
        String patientName = order.getEncounter().getPatientFullNameSnapshot();
        Long amount = invoice.getTotalAmount();
        Long invoiceId = invoice.getId();
        String invoiceCode = invoice.getCode();
        Long branchId = order.getBranch() != null ? order.getBranch().getId() : null;
        String encounterStatus = order.getEncounter() != null && order.getEncounter().getStatus() != null
                ? order.getEncounter().getStatus().name()
                : EncounterStatus.WAITING_RESULTS.name();

        afterCommitExecutor.execute(() -> {
            realtimeEventPublisher.publishDepartmentQueueUpdated(branchId, departmentCodes);
            realtimeEventPublisher.publishDoctorEncounterUpdated(doctorProfileId, encounterId, encounterStatus);
            realtimeEventPublisher.publishEncounterChannel(
                    encounterId,
                    "PAYMENT_CONFIRMED",
                    Map.of(
                            "status", encounterStatus,
                            "invoiceId", invoiceId,
                            "invoiceCode", invoiceCode
                    )
            );
            realtimeEventPublisher.publishCashierOrderEvent(
                    branchId,
                    "INVOICE_PAID",
                    serviceOrderId,
                    serviceOrderCode,
                    patientName,
                    amount,
                    invoiceId,
                    invoiceCode
            );
            internalNotificationService.notifyRole(
                    UserRole.CASHIER,
                    "INVOICE_PAID",
                    InternalNotificationService.SEVERITY_SUCCESS,
                    "Hóa đơn đã thanh toán",
                    "Hóa đơn " + invoiceCode + " của phiếu " + serviceOrderCode + " đã được thanh toán.",
                    "/app/cashier/invoices",
                    "INVOICE",
                    invoiceId
            );
            notifyDoctorPaymentConfirmed(order, invoice);
            notifyServiceDeskPaymentConfirmed(order, invoice);
        });
    }

    private void publishInvoiceCreatedRealtime(Invoice invoice) {
        ServiceOrder order = invoice.getServiceOrder();
        Long branchId = order.getBranch() != null ? order.getBranch().getId() : null;
        afterCommitExecutor.execute(() -> realtimeEventPublisher.publishCashierOrderEvent(
                branchId,
                "INVOICE_CREATED",
                order.getId(),
                order.getCode(),
                order.getEncounter() != null ? order.getEncounter().getPatientFullNameSnapshot() : null,
                invoice.getTotalAmount(),
                invoice.getId(),
                invoice.getCode()
        ));
    }

    private void notifyDoctorPaymentConfirmed(ServiceOrder order, Invoice invoice) {
        if (order.getEncounter() == null || order.getEncounter().getDoctor() == null) {
            return;
        }

        userRepository.findByDoctorProfile_Id(order.getEncounter().getDoctor().getId())
                      .ifPresent(doctorUser -> internalNotificationService.notifyUser(
                              doctorUser.getId(),
                              "SERVICE_ORDER_PAID",
                              InternalNotificationService.SEVERITY_INFO,
                              "Phiếu chỉ định đã thanh toán",
                              "Bệnh nhân " + order.getEncounter().getPatientFullNameSnapshot() + " đã thanh toán, chờ trả kết quả.",
                              "/app/doctor/encounters/" + order.getEncounter().getId(),
                              "ENCOUNTER",
                              order.getEncounter().getId()
                      ));
    }

    private void notifyServiceDeskPaymentConfirmed(ServiceOrder order, Invoice invoice) {
        String message = "Phiếu chỉ định " + order.getCode() + " đã thanh toán và sẵn sàng thực hiện.";

        internalNotificationService.notifyRole(
                UserRole.SERVICE_TECHNICIAN,
                "SERVICE_ORDER_READY",
                InternalNotificationService.SEVERITY_INFO,
                "Có phiếu chờ thực hiện",
                message,
                "/app/service-desk/results",
                "SERVICE_ORDER",
                order.getId()
        );
        internalNotificationService.notifyRole(
                UserRole.OPERATIONS_ADMIN,
                "SERVICE_ORDER_READY",
                InternalNotificationService.SEVERITY_INFO,
                "Có phiếu chờ thực hiện",
                message,
                "/app/service-desk/results",
                "SERVICE_ORDER",
                order.getId()
        );
    }

    private void enrichVnpayNote(Invoice invoice, String transactionNo, String payDate) {
        StringBuilder note = new StringBuilder();
        if (invoice.getNote() != null && !invoice.getNote().isBlank()) {
            note.append(invoice.getNote().trim());
        }
        if (transactionNo != null && !transactionNo.isBlank()) {
            if (note.length() > 0) note.append(' ');
            note.append("[VNPAY txnNo=").append(transactionNo).append(']');
        }
        if (payDate != null && !payDate.isBlank()) {
            if (note.length() > 0) note.append(' ');
            note.append("[payDate=").append(formatVnpPayDate(payDate)).append(']');
        }
        if (note.length() > 0) {
            invoice.setNote(note.toString());
        }
    }

    private String formatVnpPayDate(String payDate) {
        try {
            return LocalDateTime.parse(payDate, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).toString();
        } catch (Exception ignored) {
            return payDate;
        }
    }

    private String generateInvoiceCode(ServiceOrder order) {
        int random = secureRandom.nextInt(10000);
        return "INV-%d-%04d-%04d".formatted(
                System.currentTimeMillis(),
                order.getId() % 10000,
                random
        );
    }

    private String generatePaymentReference() {
        return "PC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddHHmmss", Locale.ROOT))
                + String.format(Locale.ROOT, "%03d", secureRandom.nextInt(1000));
    }

    private InvoiceResponse toResponse(Invoice entity, boolean includePaymentAssets) {
        PaymentIntent paymentIntent = null;
        if (entity.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            paymentIntent = paymentIntentRepository.findByInvoice_Id(entity.getId()).orElse(null);
        }

        String qrPayload = includePaymentAssets && paymentIntent != null ? paymentIntent.getQrPayload() : null;
        String qrCodeBase64 = null;
        if (includePaymentAssets && entity.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            byte[] qr = billingQrService.generatePaymentQr(entity);
            if (qr != null && qr.length > 0) {
                qrCodeBase64 = Base64.getEncoder().encodeToString(qr);
            }
        }

        return InvoiceResponse.builder()
                              .id(entity.getId())
                              .code(entity.getCode())
                              .serviceOrderId(entity.getServiceOrder().getId())
                              .serviceOrderCode(entity.getServiceOrder().getCode())
                              .encounterId(entity.getServiceOrder().getEncounter().getId())
                              .patientName(entity.getServiceOrder().getEncounter().getPatient().getFullName())
                              .doctorName(entity.getServiceOrder().getEncounter().getDoctor().getFullName())
                              .branchName(entity.getServiceOrder().getBranch().getNameVn())
                              .subtotalAmount(entity.getSubtotalAmount())
                              .discountAmount(entity.getDiscountAmount())
                              .taxAmount(entity.getTaxAmount())
                              .totalAmount(entity.getTotalAmount())
                              .items(entity.getItems() != null ? entity.getItems().stream().map(i ->
                                      com.PrimeCare.PrimeCare.modules.billing.dto.response.InvoiceItemResponse.builder()
                                              .id(i.getId())
                                              .referenceType(i.getReferenceType().name())
                                              .referenceId(i.getReferenceId())
                                              .nameSnapshot(i.getNameSnapshot())
                                              .unitPrice(i.getUnitPrice())
                                              .quantity(i.getQuantity())
                                              .taxRate(i.getTaxRate())
                                              .subtotalAmount(i.getSubtotalAmount())
                                              .taxAmount(i.getTaxAmount())
                                              .totalAmount(i.getTotalAmount())
                                              .build()
                              ).toList() : new ArrayList<>())
                              .paymentMethod(entity.getPaymentMethod())
                              .paymentStatus(entity.getPaymentStatus())
                              .paymentReference(entity.getPaymentReference())
                              .transferContent(entity.getTransferContent())
                              .paymentReviewReason(entity.getPaymentReviewReason())
                              .bankCode(includePaymentAssets && paymentIntent != null ? paymentIntent.getBankCode() : null)
                              .bankAccountNo(includePaymentAssets && paymentIntent != null ? paymentIntent.getBankAccountNo() : null)
                              .bankAccountName(includePaymentAssets && paymentIntent != null ? paymentIntent.getBankAccountName() : null)
                              .qrPayload(qrPayload)
                              .qrCodeBase64(qrCodeBase64)
                              .vnpTxnRef(entity.getVnpTxnRef())
                              .vnpPaymentUrl(entity.getVnpPaymentUrl())
                              .paymentDetectedAt(entity.getPaymentDetectedAt())
                              .paidAt(entity.getPaidAt())
                              .createdAt(entity.getCreatedAt())
                              .build();
    }

    private CashierServiceOrderResponse toCashierServiceOrderResponse(ServiceOrder order, Invoice invoice) {
        return CashierServiceOrderResponse.builder()
                                          .id(order.getId())
                                          .code(order.getCode())
                                          .encounterId(order.getEncounter() != null ? order.getEncounter().getId() : null)
                                          .patientName(order.getEncounter() != null ? order.getEncounter().getPatientFullNameSnapshot() : null)
                                          .doctorName(
                                                  order.getEncounter() != null && order.getEncounter().getDoctor() != null
                                                          ? order.getEncounter().getDoctor().getFullName()
                                                          : null
                                          )
                                          .branchName(
                                                  order.getBranch() != null
                                                          ? (order.getBranch().getNameVn() != null ? order.getBranch().getNameVn() : order.getBranch().getNameEn())
                                                          : null
                                          )
                                          .status(order.getStatus())
                                          .paymentStatus(order.getPaymentStatus())
                                          .invoiced(invoice != null)
                                          .invoiceId(invoice != null ? invoice.getId() : null)
                                          .invoiceCode(invoice != null ? invoice.getCode() : null)
                                          .estimatedTotalAmount(order.getEstimatedTotalAmount())
                                          .note(order.getNote())
                                          .orderedAt(order.getOrderedAt())
                                          .createdAt(order.getCreatedAt())
                                          .items(order.getItems().stream().map(i ->
                                                  ServiceOrderItemResponse.builder()
                                                                          .id(i.getId())
                                                                          .medicalServiceId(i.getMedicalService().getId())
                                                                          .serviceCode(i.getServiceCodeSnapshot())
                                                                          .serviceNameVn(i.getServiceNameVnSnapshot())
                                                                          .serviceNameEn(i.getServiceNameEnSnapshot())
                                                                          .price(i.getPriceSnapshot())
                                                                          .quantity(i.getQuantity())
                                                                          .lineTotalAmount(i.getLineTotalAmount())
                                                                          .departmentCode(i.getAssignedDepartmentCode())
                                                                          .queueNo(i.getQueueNo())
                                                                          .status(i.getStatus())
                                                                          .resultStatus(i.getResultStatus())
                                                                          .build()
                                          ).toList())
                                          .build();
    }

    private LocalDateTime startOfDay(LocalDate date) {
        return date != null ? date.atStartOfDay() : null;
    }

    private LocalDateTime dayAfterStart(LocalDate date) {
        return date != null ? date.plusDays(1).atStartOfDay() : null;
    }

    private long durationMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private <T> PageResponse.Meta toMeta(Page<T> page, Pageable pageable) {
        return PageResponse.Meta.builder()
                                .page(page.getNumber())
                                .size(page.getSize())
                                .totalItems(page.getTotalElements())
                                .totalPages(page.getTotalPages())
                                .hasNext(page.hasNext())
                                .hasPrev(page.hasPrevious())
                                .sort(pageable.getSort().toString())
                                .build();
    }

    private Map<String, Object> snapshotInvoice(Invoice invoice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", invoice.getId());
        data.put("code", invoice.getCode());
        data.put("serviceOrderId", invoice.getServiceOrder() != null ? invoice.getServiceOrder().getId() : null);
        data.put("cashierId", invoice.getCashier() != null ? invoice.getCashier().getId() : null);
        data.put("paymentMethod", invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : null);
        data.put("paymentStatus", invoice.getPaymentStatus() != null ? invoice.getPaymentStatus().name() : null);
        data.put("paymentReference", invoice.getPaymentReference());
        data.put("transferContent", invoice.getTransferContent());
        data.put("paymentDetectedAt", invoice.getPaymentDetectedAt());
        data.put("paymentReviewReason", invoice.getPaymentReviewReason());
        data.put("subtotalAmount", invoice.getSubtotalAmount());
        data.put("discountAmount", invoice.getDiscountAmount());
        data.put("totalAmount", invoice.getTotalAmount());
        data.put("paidAt", invoice.getPaidAt());
        data.put("vnpTxnRef", invoice.getVnpTxnRef());
        data.put("vnpPaymentUrl", invoice.getVnpPaymentUrl());
        return data;
    }

    @Transactional
    public InvoiceResponse refundInvoice(Long invoiceId, Long refundAmount, String reason, Long cashierUserId) {
        Invoice invoice = invoiceRepository.findWithLockById(invoiceId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));

        if (refundAmount == null || refundAmount <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Số tiền hoàn phải lớn hơn 0");
        }

        if (invoice.getPaymentStatus() != PaymentStatus.PAID) {
            throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, "Chỉ có thể hoàn tiền cho hóa đơn đã thanh toán");
        }

        if (refundAmount > invoice.getTotalAmount()) {
            throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, "Số tiền hoàn lớn hơn tổng hóa đơn");
        }

        User cashier = userRepository.findById(cashierUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        Map<String, Object> before = snapshotInvoice(invoice);
        var previousPaymentStatus = invoice.getPaymentStatus();

        RefundRecord refund = RefundRecord.builder()
                .invoice(invoice)
                .refundAmount(refundAmount)
                .reason(reason)
                .approvedByUser(cashier)
                .build();
        refundRecordRepository.save(refund);

        invoice.setPaymentStatus(PaymentStatus.REFUNDED);
        RefundCascadeResult cascadeResult = applyRefundedState(invoice);
        Invoice saved = invoiceRepository.save(invoice);

        invoiceStatusHistoryService.record(saved, previousPaymentStatus, saved.getPaymentStatus(), cashier, "Refund: " + reason);
        auditLogService.log(cashier, "REFUND", "INVOICE", saved.getId(), before, snapshotInvoice(saved));
        publishRefundRealtime(saved, cascadeResult);

        return toResponse(saved, true);
    }

    private RefundCascadeResult applyRefundedState(Invoice invoice) {
        ServiceOrder order = orderRepository.findWithLockById(invoice.getServiceOrder().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_ORDER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();

        order.setPaymentStatus(PaymentStatus.REFUNDED);
        order.setStatus(ServiceOrderStatus.CANCELLED);
        order.setCancelledAt(now);

        for (ServiceOrderItem item : order.getItems()) {
            item.setStatus(ServiceOrderItemStatus.CANCELLED);
            if (item.getCancelledAt() == null) {
                item.setCancelledAt(now);
            }
        }

        EncounterStatus previousEncounterStatus = null;
        AppointmentStatus previousAppointmentStatus = null;
        if (order.getEncounter() != null) {
            previousEncounterStatus = order.getEncounter().getStatus();
            if (previousEncounterStatus != EncounterStatus.CANCELLED) {
                if (previousEncounterStatus == EncounterStatus.COMPLETED) {
                    order.getEncounter().setCompletedAt(null);
                    Appointment appointment = order.getEncounter().getAppointment();
                    if (appointment != null) {
                        previousAppointmentStatus = appointment.getStatus();
                        appointment.setStatus(AppointmentStatus.CHECKED_IN);
                        appointment.setCompletedAt(null);
                    }
                    order.getEncounter().setStatus(EncounterStatus.REOPENED);
                }

                EncounterStatus nextEncounterStatus = encounterWorkflowService.resolveStatus(order.getEncounter());
                order.getEncounter().setStatus(nextEncounterStatus);
            }
        }

        return new RefundCascadeResult(order, previousEncounterStatus, previousAppointmentStatus);
    }

    private void publishRefundRealtime(Invoice invoice, RefundCascadeResult cascadeResult) {
        ServiceOrder order = cascadeResult.order();
        var departmentCodes = order.getItems().stream()
                .map(ServiceOrderItem::getAssignedDepartmentCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();

        Long encounterId = order.getEncounter() != null ? order.getEncounter().getId() : null;
        Long doctorProfileId = order.getEncounter() != null && order.getEncounter().getDoctor() != null
                ? order.getEncounter().getDoctor().getId()
                : null;
        String encounterStatus = order.getEncounter() != null && order.getEncounter().getStatus() != null
                ? order.getEncounter().getStatus().name()
                : null;
        Long serviceOrderId = order.getId();
        String serviceOrderCode = order.getCode();
        String patientName = order.getEncounter() != null ? order.getEncounter().getPatientFullNameSnapshot() : null;
        Long amount = invoice.getTotalAmount();
        Long invoiceId = invoice.getId();
        String invoiceCode = invoice.getCode();
        Appointment appointment = order.getEncounter() != null ? order.getEncounter().getAppointment() : null;
        AppointmentStatus previousAppointmentStatus = cascadeResult.previousAppointmentStatus();
        Long branchId = order.getBranch() != null ? order.getBranch().getId() : null;

        afterCommitExecutor.execute(() -> {
            realtimeEventPublisher.publishDepartmentQueueUpdated(branchId, departmentCodes);

            if (doctorProfileId != null && encounterId != null && encounterStatus != null) {
                realtimeEventPublisher.publishDoctorEncounterUpdated(doctorProfileId, encounterId, encounterStatus);
            }

            if (encounterId != null) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("status", encounterStatus);
                payload.put("previousStatus", cascadeResult.previousEncounterStatus() != null
                        ? cascadeResult.previousEncounterStatus().name()
                        : null);
                payload.put("invoiceId", invoiceId);
                payload.put("invoiceCode", invoiceCode);
                payload.put("serviceOrderId", serviceOrderId);
                payload.put("serviceOrderCode", serviceOrderCode);
                realtimeEventPublisher.publishEncounterChannel(encounterId, "PAYMENT_REFUNDED", payload);
            }

            realtimeEventPublisher.publishCashierOrderEvent(
                    branchId,
                    "INVOICE_REFUNDED",
                    serviceOrderId,
                    serviceOrderCode,
                    patientName,
                    amount,
                    invoiceId,
                    invoiceCode
            );

            if (previousAppointmentStatus != null) {
                publishAppointmentRealtime(appointment, previousAppointmentStatus);
            }
        });
    }

    private void publishAppointmentRealtime(Appointment appointment, AppointmentStatus previousStatus) {
        if (appointment == null || appointment.getBranch() == null || appointment.getVisitDate() == null) {
            return;
        }

        realtimeEventPublisher.publishAppointmentSummaryChanged(
                appointment.getBranch().getId(),
                appointment.getVisitDate()
        );
        realtimeEventPublisher.publishAppointmentUpdated(
                appointment.getId(),
                appointment.getBranch().getId(),
                appointment.getDoctor() != null ? appointment.getDoctor().getId() : null,
                appointment.getVisitDate(),
                appointment.getSession() != null ? appointment.getSession().name() : null,
                previousStatus != null ? previousStatus.name() : null,
                appointment.getStatus() != null ? appointment.getStatus().name() : null,
                appointment.getArrivalStatus() != null ? appointment.getArrivalStatus().name() : null,
                appointment.getQueueNo(),
                appointment.getReceptionQueueNo(),
                appointment.getArrivedAt() != null ? appointment.getArrivedAt().toString() : null,
                appointment.getArrivedBy() != null ? resolveUserDisplayName(appointment.getArrivedBy()) : null,
                appointment.getCheckedInAt() != null ? appointment.getCheckedInAt().toString() : null,
                appointment.getCheckedInBy() != null ? resolveUserDisplayName(appointment.getCheckedInBy()) : null,
                appointment.getConfirmedAt() != null ? appointment.getConfirmedAt().toString() : null,
                appointment.getConfirmedBy() != null ? resolveUserDisplayName(appointment.getConfirmedBy()) : null
        );
    }

    private String resolveUserDisplayName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getStaffProfile() != null && user.getStaffProfile().getFullName() != null) {
            return user.getStaffProfile().getFullName();
        }
        if (user.getDoctorProfile() != null && user.getDoctorProfile().getFullName() != null) {
            return user.getDoctorProfile().getFullName();
        }
        return user.getEmail();
    }

    private record RefundCascadeResult(
            ServiceOrder order,
            EncounterStatus previousEncounterStatus,
            AppointmentStatus previousAppointmentStatus
    ) {
    }

    private void appendNote(Invoice invoice, String addition) {
        if (addition == null || addition.isBlank()) {
            return;
        }
        if (invoice.getNote() == null || invoice.getNote().isBlank()) {
            invoice.setNote(addition.trim());
            return;
        }
        invoice.setNote((invoice.getNote().trim() + " " + addition.trim()).trim());
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String optionalText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
