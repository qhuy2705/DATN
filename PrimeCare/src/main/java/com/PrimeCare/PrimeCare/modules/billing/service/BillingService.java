package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.BankTransferTransactionRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.ChangeInvoicePaymentMethodRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.PayInvoiceRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.request.RefundInvoiceItemsRequest;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.CashierSummaryResponse;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.InvoiceResponse;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.PaymentApplyResult;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.RefundableInvoiceItemResponse;
import com.PrimeCare.PrimeCare.modules.billing.dto.response.RefundableInvoiceItemsResponse;
import com.PrimeCare.PrimeCare.modules.billing.entity.BankTransaction;
import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.billing.entity.InvoiceItem;
import com.PrimeCare.PrimeCare.modules.billing.entity.PaymentIntent;
import com.PrimeCare.PrimeCare.modules.billing.entity.RefundRecord;
import com.PrimeCare.PrimeCare.modules.billing.entity.RefundRecordItem;
import com.PrimeCare.PrimeCare.modules.billing.repository.BankTransactionRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.PaymentIntentRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.RefundRecordItemRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.RefundRecordRepository;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterWorkflowService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.prescription.entity.PrescriptionItem;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionItemRepository;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.modules.service_order.dto.response.CashierServiceOrderResponse;
import com.PrimeCare.PrimeCare.modules.service_order.dto.response.ServiceOrderItemResponse;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderItemRepository;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.modules.service_order.service.DepartmentQueueAllocator;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BankTransactionStatus;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.InvoiceItemRefundStatus;
import com.PrimeCare.PrimeCare.shared.enums.InvoiceItemSourceType;
import com.PrimeCare.PrimeCare.shared.enums.PaymentIntentProvider;
import com.PrimeCare.PrimeCare.shared.enums.PaymentIntentStatus;
import com.PrimeCare.PrimeCare.shared.enums.PaymentMethod;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final RefundRecordItemRepository refundRecordItemRepository;
    private final EncounterWorkflowService encounterWorkflowService;
    private final InternalNotificationService internalNotificationService;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final ServiceOrderItemRepository serviceOrderItemRepository;
    private final ServiceResultRepository serviceResultRepository;

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

        long invoicesCreatedInRange = invoiceSummary != null ? invoiceSummary.getInvoicesCreatedInRange() : 0;
        long unpaidInvoiceCount = invoiceSummary != null ? invoiceSummary.getUnpaidInvoiceCount() : 0;
        long pendingConfirmationInvoiceCount = invoiceSummary != null ? invoiceSummary.getPendingConfirmationInvoiceCount() : 0;
        long paymentReviewInvoiceCount = invoiceSummary != null ? invoiceSummary.getPaymentReviewInvoiceCount() : 0;
        long paidInvoicesInRange = invoiceSummary != null ? invoiceSummary.getPaidInvoicesInRange() : 0;
        long grossPaidRevenueInRange = invoiceSummary != null ? invoiceSummary.getGrossPaidRevenueInRange() : 0;
        long refundedAmountForPaidInvoicesInRange = invoiceSummary != null ? invoiceSummary.getRefundedAmountForPaidInvoicesInRange() : 0;
        long netPaidRevenueInRange = invoiceSummary != null ? invoiceSummary.getNetPaidRevenueInRange() : 0;
        long refundsProcessedInRange = invoiceSummary != null ? invoiceSummary.getRefundsProcessedInRange() : 0;

        CashierSummaryResponse response = CashierSummaryResponse.builder()
                .invoicesCreatedInRange(invoicesCreatedInRange)
                .invoiceCount(invoicesCreatedInRange)
                .pendingInvoices(unpaidInvoiceCount + pendingConfirmationInvoiceCount + paymentReviewInvoiceCount)
                .unpaidInvoiceCount(unpaidInvoiceCount)
                .pendingConfirmationInvoiceCount(pendingConfirmationInvoiceCount)
                .paymentReviewInvoiceCount(paymentReviewInvoiceCount)
                .paidInvoicesInRange(paidInvoicesInRange)
                .paidInvoiceCount(paidInvoicesInRange)
                .refundedInvoiceCount(invoiceSummary != null ? invoiceSummary.getRefundedInvoiceCount() : 0)
                .grossPaidRevenueInRange(grossPaidRevenueInRange)
                .refundedAmountForPaidInvoicesInRange(refundedAmountForPaidInvoicesInRange)
                .netPaidRevenueInRange(netPaidRevenueInRange)
                .refundsProcessedInRange(refundsProcessedInRange)
                .paidRevenueInRange(netPaidRevenueInRange)
                .paidRevenue(netPaidRevenueInRange)
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

    @Transactional(readOnly = true)
    public RefundableInvoiceItemsResponse getRefundableItems(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));

        List<RefundableInvoiceItemResponse> items = invoice.getItems() != null
                ? invoice.getItems().stream()
                .map(item -> evaluateRefundableItem(invoice, item, false).toResponse())
                .toList()
                : List.of();

        long refundedAmount = refundedAmount(invoice);
        return RefundableInvoiceItemsResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceCode(invoice.getCode())
                .paymentStatus(invoice.getPaymentStatus())
                .totalAmount(invoice.getTotalAmount())
                .refundedAmount(refundedAmount)
                .remainingAmount(remainingAmount(invoice, refundedAmount))
                .items(items)
                .build();
    }

    @Transactional
    public InvoiceResponse refundInvoiceItems(Long invoiceId, RefundInvoiceItemsRequest request, Long cashierUserId) {
        String reason = optionalText(request != null ? request.getReason() : null, null);
        if (reason == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Lý do hoàn tiền không được để trống");
        }
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Danh sách mục hoàn tiền không được để trống");
        }

        List<Long> requestedItemIds = request.getItems().stream()
                .map(RefundInvoiceItemsRequest.Item::getInvoiceItemId)
                .toList();
        if (requestedItemIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Mục hoàn tiền không hợp lệ");
        }
        Set<Long> uniqueItemIds = new LinkedHashSet<>(requestedItemIds);
        if (uniqueItemIds.size() != requestedItemIds.size()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Danh sách mục hoàn tiền bị trùng");
        }

        Invoice invoice = invoiceRepository.findWithLockById(invoiceId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
        return refundInvoiceItemsInternal(invoice, uniqueItemIds, reason, cashierUserId, "Selected invoice item refund");
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
                    .sourceItemType(InvoiceItemSourceType.SERVICE_ORDER_ITEM)
                    .sourceItemId(orderItem.getId())
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
        auditLogService.log(cashier, "CREATE_INVOICE", "INVOICE", saved.getId(), null, snapshotInvoice(saved));
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
        if (invoice.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Hóa đơn đã hoàn tiền một phần, không thể xác nhận thanh toán lại");
        }

        if (invoice.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            throw new ApiException(
                    ErrorCode.INVOICE_INVALID_STATUS,
                    "Hóa đơn chuyển khoản cần xác nhận qua luồng đối soát"
            );
        }
        if (invoice.getPaymentMethod() == PaymentMethod.VNPAY) {
            throw new ApiException(
                    ErrorCode.INVOICE_INVALID_STATUS,
                    "Hóa đơn VNPAY chỉ được xác nhận qua callback/IPN hợp lệ"
            );
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

        auditLogService.log(cashier, "MARK_INVOICE_PAID", "INVOICE", saved.getId(), before, snapshotInvoice(saved));
        publishPaidRealtime(saved);

        return toResponse(saved, true);
    }

    @Transactional
    public InvoiceResponse markPaidFromVnpay(String txnRef) {
        throw new ApiException(
                ErrorCode.INVOICE_INVALID_STATUS,
                "Hóa đơn VNPAY chỉ được xác nhận qua callback/IPN hợp lệ"
        );
    }

    @Transactional
    public InvoiceResponse changeInvoicePaymentMethod(
            Long invoiceId,
            Long cashierUserId,
            ChangeInvoicePaymentMethodRequest req
    ) {
        PaymentMethod newPaymentMethod = req != null ? req.getPaymentMethod() : null;
        if (newPaymentMethod == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Payment method is required.");
        }
        if (!isChangeablePaymentMethod(newPaymentMethod)) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "Payment method must be one of CASH, BANK_TRANSFER, VNPAY"
            );
        }

        Invoice invoice = invoiceRepository.findWithLockById(invoiceId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));

        if (invoice.getPaymentMethod() == newPaymentMethod) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Payment method is already selected.");
        }

        validatePaymentMethodChangeAllowed(invoice);

        User cashier = userRepository.findById(cashierUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        Map<String, Object> before = snapshotPaymentMethodChangeBefore(invoice);
        PaymentStatus previousStatus = invoice.getPaymentStatus();

        var vnpayInit = newPaymentMethod == PaymentMethod.VNPAY
                ? vnpayPaymentService.init(invoice.getCode(), invoice.getTotalAmount(), req.getReturnUrl())
                : null;

        cancelPendingPaymentIntent(invoice.getId());
        clearBankTransferArtifacts(invoice);
        clearVnpayArtifacts(invoice);
        invoice.setPaidAt(null);
        invoice.setPaymentMethod(newPaymentMethod);

        if (newPaymentMethod == PaymentMethod.CASH) {
            invoice.setPaymentStatus(PaymentStatus.UNPAID);
        } else if (newPaymentMethod == PaymentMethod.BANK_TRANSFER) {
            invoice.setPaymentStatus(PaymentStatus.PENDING_CONFIRMATION);
            invoice.setPaymentReference(generatePaymentReference());
            invoice.setTransferContent(billingQrService.buildPaymentContent(invoice));
        } else if (newPaymentMethod == PaymentMethod.VNPAY) {
            invoice.setPaymentStatus(PaymentStatus.UNPAID);
            invoice.setVnpTxnRef(vnpayInit.txnRef());
            invoice.setVnpPaymentUrl(vnpayInit.paymentUrl());
        }

        Invoice saved = invoiceRepository.save(invoice);
        if (saved.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            upsertPaymentIntent(saved, null, PaymentIntentStatus.PENDING, null, null);
        }

        invoiceStatusHistoryService.record(
                saved,
                previousStatus,
                saved.getPaymentStatus(),
                cashier,
                "Invoice payment method changed"
        );
        auditLogService.log(
                cashier,
                "CHANGE_INVOICE_PAYMENT_METHOD",
                "INVOICE",
                saved.getId(),
                before,
                snapshotPaymentMethodChangeAfter(saved)
        );
        publishInvoicePaymentMethodChangedRealtime(saved);

        return toResponse(saved, true);
    }

    @Transactional
    public PaymentApplyResult confirmVnpayPayment(String txnRef, Long paidAmount, String transactionNo, String payDate) {
        Invoice invoice = invoiceRepository.findWithLockByVnpTxnRef(txnRef)
                                           .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));

        if (invoice.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Hóa đơn này không sử dụng VNPAY");
        }

        if (paidAmount == null) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Thiếu số tiền thanh toán VNPAY");
        }

        if (invoice.getTotalAmount() != null && !invoice.getTotalAmount().equals(paidAmount)) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Số tiền thanh toán VNPAY không khớp hóa đơn");
        }

        if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
            return new PaymentApplyResult(toResponse(invoice, true), true);
        }
        if (invoice.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Hóa đơn đã hoàn tiền một phần, không thể xác nhận thanh toán lại");
        }

        Map<String, Object> before = snapshotInvoice(invoice);
        var previousPaymentStatus = invoice.getPaymentStatus();
        LocalDateTime paidTime = parseVnpayPayDate(payDate).orElseGet(LocalDateTime::now);
        applyPaidState(invoice, paidTime);
        enrichVnpayNote(invoice, transactionNo, payDate);
        Invoice saved = invoiceRepository.save(invoice);
        invoiceStatusHistoryService.record(saved, previousPaymentStatus, saved.getPaymentStatus(), null, "VNPAY payment confirmed");

        auditLogService.log(null, "MARK_INVOICE_PAID", "INVOICE", saved.getId(), before, snapshotInvoice(saved));
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
        if (invoice.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Hóa đơn đã hoàn tiền một phần, không thể xác nhận thanh toán lại");
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
    }

    private boolean isChangeablePaymentMethod(PaymentMethod paymentMethod) {
        return paymentMethod == PaymentMethod.CASH
                || paymentMethod == PaymentMethod.BANK_TRANSFER
                || paymentMethod == PaymentMethod.VNPAY;
    }

    private void validatePaymentMethodChangeAllowed(Invoice invoice) {
        if (invoice == null || invoice.getPaymentStatus() == null) {
            throwPaymentMethodChangeNotAllowed();
        }

        if (hasPaymentDetectionMarkers(invoice)
                || hasUnsafePaymentIntent(invoice)
                || hasMatchedBankTransaction(invoice)) {
            throwPaymentMethodChangeNotAllowed();
        }

        if (invoice.getPaymentStatus() == PaymentStatus.UNPAID) {
            return;
        }

        if (invoice.getPaymentStatus() == PaymentStatus.PENDING_CONFIRMATION
                && invoice.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            return;
        }

        throwPaymentMethodChangeNotAllowed();
    }

    private boolean hasPaymentDetectionMarkers(Invoice invoice) {
        return invoice.getPaidAt() != null
                || invoice.getPaymentDetectedAt() != null
                || optionalText(invoice.getPaymentReviewReason(), null) != null;
    }

    private boolean hasUnsafePaymentIntent(Invoice invoice) {
        if (invoice.getId() == null) {
            return false;
        }
        return paymentIntentRepository.findWithLockByInvoice_Id(invoice.getId())
                .map(intent -> intent.getStatus() == PaymentIntentStatus.DETECTED
                        || intent.getStatus() == PaymentIntentStatus.CONFIRMED
                        || intent.getStatus() == PaymentIntentStatus.REVIEW
                        || intent.getDetectedAt() != null
                        || intent.getConfirmedAt() != null
                        || optionalText(intent.getMatchedTransactionRef(), null) != null)
                .orElse(false);
    }

    private boolean hasMatchedBankTransaction(Invoice invoice) {
        return invoice.getId() != null
                && bankTransactionRepository.existsByMatchedInvoice_IdAndStatusIn(
                        invoice.getId(),
                        List.of(
                                BankTransactionStatus.RECEIVED,
                                BankTransactionStatus.MATCHED,
                                BankTransactionStatus.REVIEW
                        )
                );
    }

    private void throwPaymentMethodChangeNotAllowed() {
        throw new ApiException(
                ErrorCode.INVOICE_INVALID_STATUS,
                "Cannot change payment method after payment has been confirmed or is under review."
        );
    }

    private void cancelPendingPaymentIntent(Long invoiceId) {
        if (invoiceId == null) {
            return;
        }
        paymentIntentRepository.findWithLockByInvoice_Id(invoiceId)
                .filter(intent -> intent.getStatus() == PaymentIntentStatus.PENDING)
                .ifPresent(intent -> {
                    intent.setStatus(PaymentIntentStatus.CANCELLED);
                    intent.setExpiresAt(LocalDateTime.now());
                    paymentIntentRepository.save(intent);
                });
    }

    private void clearBankTransferArtifacts(Invoice invoice) {
        invoice.setPaymentReference(null);
        invoice.setTransferContent(null);
        invoice.setPaymentDetectedAt(null);
        invoice.setPaymentReviewReason(null);
    }

    private void clearVnpayArtifacts(Invoice invoice) {
        invoice.setVnpTxnRef(null);
        invoice.setVnpPaymentUrl(null);
    }

    private InvoiceResponse refundInvoiceItemsInternal(
            Invoice invoice,
            Set<Long> requestedItemIds,
            String reason,
            Long cashierUserId,
            String historyNote
    ) {
        if (!isRefundableInvoiceStatus(invoice.getPaymentStatus())) {
            throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, "Chỉ có thể hoàn tiền hóa đơn đã thanh toán");
        }
        if (invoice.getItems() == null || invoice.getItems().isEmpty()) {
            throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, "Hóa đơn không có dòng chi tiết để hoàn tiền theo mục");
        }

        Map<Long, InvoiceItem> itemById = invoice.getItems().stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(InvoiceItem::getId, Function.identity(), (left, ignored) -> left));

        List<RefundableEvaluation> evaluations = new ArrayList<>();
        for (Long invoiceItemId : requestedItemIds) {
            InvoiceItem item = itemById.get(invoiceItemId);
            if (item == null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Mục hóa đơn không thuộc hóa đơn này");
            }
            RefundableEvaluation evaluation = evaluateRefundableItem(invoice, item, true);
            if (!evaluation.refundable()) {
                throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, evaluation.notRefundableReason());
            }
            evaluations.add(evaluation);
        }

        long refundAmount = evaluations.stream().mapToLong(RefundableEvaluation::refundableAmount).sum();
        if (refundAmount <= 0) {
            throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, "Mục hóa đơn đã được hoàn tiền");
        }

        User cashier = userRepository.findById(cashierUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        Map<String, Object> before = snapshotInvoiceItemRefund(invoice, evaluations);
        PaymentStatus previousStatus = invoice.getPaymentStatus();
        LocalDateTime now = LocalDateTime.now();

        RefundRecord refundRecord = RefundRecord.builder()
                .invoice(invoice)
                .refundAmount(refundAmount)
                .reason(reason)
                .approvedByUser(cashier)
                .refundedAt(now)
                .build();
        refundRecordRepository.save(refundRecord);

        List<RefundRecordItem> recordItems = new ArrayList<>();
        for (RefundableEvaluation evaluation : evaluations) {
            InvoiceItem item = evaluation.invoiceItem();
            item.setRefundedAmount(item.getTotalAmount());
            item.setRefundStatus(InvoiceItemRefundStatus.REFUNDED);
            item.setRefundedAt(now);

            if (evaluation.serviceOrderItem() != null) {
                ServiceOrderItem source = evaluation.serviceOrderItem();
                source.setStatus(ServiceOrderItemStatus.CANCELLED);
                if (source.getCancelledAt() == null) {
                    source.setCancelledAt(now);
                }
                source.setRefundedAt(now);
                source.setRefundedByUser(cashier);
                source.setRefundReason(reason);
                serviceOrderItemRepository.save(source);
            }

            if (evaluation.prescriptionItem() != null) {
                PrescriptionItem source = evaluation.prescriptionItem();
                source.setStatus(PrescriptionItemStatus.REFUNDED);
                source.setRefundedAt(now);
                source.setRefundedByUser(cashier);
                source.setRefundReason(reason);
                prescriptionItemRepository.save(source);
            }

            recordItems.add(RefundRecordItem.builder()
                    .refundRecord(refundRecord)
                    .invoiceItem(item)
                    .sourceItemType(item.getSourceItemType())
                    .sourceItemId(item.getSourceItemId())
                    .nameSnapshot(item.getNameSnapshot())
                    .quantity(item.getQuantity())
                    .refundAmount(evaluation.refundableAmount())
                    .build());
        }
        refundRecordItemRepository.saveAll(recordItems);

        PaymentStatus nextStatus = allInvoiceItemsRefunded(invoice)
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
        invoice.setPaymentStatus(nextStatus);
        updateSourceAggregatesAfterItemRefund(invoice, now);

        Invoice saved = invoiceRepository.save(invoice);
        invoiceStatusHistoryService.record(saved, previousStatus, saved.getPaymentStatus(), cashier, historyNote);

        Map<String, Object> after = snapshotInvoiceItemRefundAfter(saved, evaluations, reason);
        auditLogService.log(cashier, "REFUND_INVOICE_ITEMS", "INVOICE", saved.getId(), before, after);
        publishInvoiceItemsRefundedRealtime(saved);

        return toResponse(saved, true);
    }

    private boolean isRefundableInvoiceStatus(PaymentStatus status) {
        return status == PaymentStatus.PAID || status == PaymentStatus.PARTIALLY_REFUNDED;
    }

    private RefundableEvaluation evaluateRefundableItem(Invoice invoice, InvoiceItem item, boolean lockSource) {
        long alreadyRefundedAmount = item.getRefundedAmount() != null ? item.getRefundedAmount() : 0L;
        long totalAmount = item.getTotalAmount() != null ? item.getTotalAmount() : 0L;
        long refundableAmount = Math.max(0L, totalAmount - alreadyRefundedAmount);

        if (!isRefundableInvoiceStatus(invoice.getPaymentStatus())) {
            return RefundableEvaluation.notRefundable(invoice, item, alreadyRefundedAmount, refundableAmount, "Invoice is not paid.");
        }
        if (refundableAmount <= 0 || item.getRefundStatus() == InvoiceItemRefundStatus.REFUNDED) {
            return RefundableEvaluation.notRefundable(invoice, item, alreadyRefundedAmount, 0L, "Invoice item has already been refunded.");
        }
        if (item.getSourceItemType() == null || item.getSourceItemId() == null) {
            return RefundableEvaluation.notRefundable(
                    invoice,
                    item,
                    alreadyRefundedAmount,
                    refundableAmount,
                    "Legacy invoice item cannot be refunded by item because source item link is missing."
            );
        }

        if (item.getSourceItemType() == InvoiceItemSourceType.SERVICE_ORDER_ITEM) {
            Optional<ServiceOrderItem> source = lockSource
                    ? serviceOrderItemRepository.findWithLockById(item.getSourceItemId())
                    : serviceOrderItemRepository.findById(item.getSourceItemId());
            if (source.isEmpty()) {
                return RefundableEvaluation.notRefundable(invoice, item, alreadyRefundedAmount, refundableAmount, "Source service item was not found.");
            }
            return evaluateServiceOrderItemRefund(invoice, item, source.get(), alreadyRefundedAmount, refundableAmount);
        }

        if (item.getSourceItemType() == InvoiceItemSourceType.PRESCRIPTION_ITEM) {
            Optional<PrescriptionItem> source = lockSource
                    ? prescriptionItemRepository.findWithLockById(item.getSourceItemId())
                    : prescriptionItemRepository.findById(item.getSourceItemId());
            if (source.isEmpty()) {
                return RefundableEvaluation.notRefundable(invoice, item, alreadyRefundedAmount, refundableAmount, "Source prescription item was not found.");
            }
            return evaluatePrescriptionItemRefund(invoice, item, source.get(), alreadyRefundedAmount, refundableAmount);
        }

        return RefundableEvaluation.notRefundable(invoice, item, alreadyRefundedAmount, refundableAmount, "Invoice item source type is not supported.");
    }

    private RefundableEvaluation evaluateServiceOrderItemRefund(
            Invoice invoice,
            InvoiceItem invoiceItem,
            ServiceOrderItem source,
            long alreadyRefundedAmount,
            long refundableAmount
    ) {
        ServiceResult result = serviceResultRepository.findByServiceOrderItem_Id(source.getId()).orElse(null);
        String reason = serviceItemNotRefundableReason(source, result);
        if (reason != null) {
            return RefundableEvaluation.notRefundable(invoice, invoiceItem, source, result, alreadyRefundedAmount, refundableAmount, reason);
        }
        return RefundableEvaluation.refundable(invoice, invoiceItem, source, result, alreadyRefundedAmount, refundableAmount);
    }

    private RefundableEvaluation evaluatePrescriptionItemRefund(
            Invoice invoice,
            InvoiceItem invoiceItem,
            PrescriptionItem source,
            long alreadyRefundedAmount,
            long refundableAmount
    ) {
        String reason = prescriptionItemNotRefundableReason(source);
        if (reason != null) {
            return RefundableEvaluation.notRefundable(invoice, invoiceItem, source, alreadyRefundedAmount, refundableAmount, reason);
        }
        return RefundableEvaluation.refundable(invoice, invoiceItem, source, alreadyRefundedAmount, refundableAmount);
    }

    private String serviceItemNotRefundableReason(ServiceOrderItem item, ServiceResult result) {
        if (item.getStatus() == ServiceOrderItemStatus.IN_PROGRESS) {
            return "Service item has already started.";
        }
        if (item.getStatus() == ServiceOrderItemStatus.DONE) {
            return "Service item has already been completed.";
        }
        if (item.getStatus() == ServiceOrderItemStatus.CANCELLED) {
            return "Service item has already been cancelled or refunded.";
        }
        if (item.getStatus() != ServiceOrderItemStatus.WAITING_EXECUTION) {
            return "Service item is not ready for refundable cancellation.";
        }
        if (item.getResultStatus() != null && item.getResultStatus() != ServiceResultStatus.DRAFT) {
            return "Service result is already completed or under verification.";
        }
        if (item.getCompletedAt() != null) {
            return "Service item has completion timestamp.";
        }
        if (hasMeaningfulServiceResult(result)) {
            return "Service result already contains clinical content or files.";
        }
        return null;
    }

    private boolean hasMeaningfulServiceResult(ServiceResult result) {
        if (result == null) {
            return false;
        }
        if (result.getStatus() == ServiceResultStatus.COMPLETED || result.getStatus() == ServiceResultStatus.VERIFIED) {
            return true;
        }
        return optionalText(result.getResultTextVn(), null) != null
                || optionalText(result.getResultTextEn(), null) != null
                || optionalText(result.getResultDataJson(), null) != null
                || optionalText(result.getFieldValuesJson(), null) != null
                || optionalText(result.getConclusionText(), null) != null
                || optionalText(result.getImpressionText(), null) != null
                || optionalText(result.getAttachmentUrl(), null) != null
                || optionalText(result.getAttachmentUrlsJson(), null) != null
                || optionalText(result.getReportPdfPath(), null) != null
                || result.getPerformedAt() != null
                || result.getVerifiedAt() != null;
    }

    private String prescriptionItemNotRefundableReason(PrescriptionItem item) {
        Prescription prescription = item.getPrescription();
        if (prescription != null && prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            return "Prescription has already been dispensed.";
        }
        PrescriptionItemStatus status = effectivePrescriptionItemStatus(item);
        if (status == PrescriptionItemStatus.DISPENSED) {
            return "Medication item has already been dispensed.";
        }
        if (status == PrescriptionItemStatus.REFUNDED || status == PrescriptionItemStatus.CANCELLED) {
            return "Medication item has already been refunded or cancelled.";
        }
        return null;
    }

    private PrescriptionItemStatus effectivePrescriptionItemStatus(PrescriptionItem item) {
        if (item.getStatus() != null) {
            return item.getStatus();
        }
        Prescription prescription = item.getPrescription();
        if (prescription != null && prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            return PrescriptionItemStatus.DISPENSED;
        }
        if (prescription != null && prescription.getStatus() == PrescriptionStatus.PAID) {
            return PrescriptionItemStatus.PAID;
        }
        return PrescriptionItemStatus.ISSUED;
    }

    private void updateSourceAggregatesAfterItemRefund(Invoice invoice, LocalDateTime now) {
        if (invoice.getServiceOrder() != null) {
            updateServiceOrderAfterItemRefund(invoice.getServiceOrder(), invoice, now);
        }
        if (invoice.getPrescription() != null) {
            updatePrescriptionAfterItemRefund(invoice.getPrescription());
        }
    }

    private void updateServiceOrderAfterItemRefund(ServiceOrder order, Invoice invoice, LocalDateTime now) {
        if (allInvoiceItemsRefunded(invoice)) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        } else if (order.getPaymentStatus() == PaymentStatus.PAID || order.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        if (order.getItems() != null && !order.getItems().isEmpty()
                && order.getItems().stream().allMatch(item -> item.getStatus() == ServiceOrderItemStatus.CANCELLED)) {
            order.setStatus(ServiceOrderStatus.CANCELLED);
            if (order.getCancelledAt() == null) {
                order.setCancelledAt(now);
            }
        }
        orderRepository.save(order);
        if (order.getEncounter() != null) {
            encounterWorkflowService.refreshStatus(order.getEncounter());
        }
    }

    private void updatePrescriptionAfterItemRefund(Prescription prescription) {
        if (prescription.getItems() == null || prescription.getItems().isEmpty()) {
            return;
        }
        boolean allRefundedOrCancelled = prescription.getItems().stream()
                .allMatch(item -> {
                    PrescriptionItemStatus status = effectivePrescriptionItemStatus(item);
                    return status == PrescriptionItemStatus.REFUNDED || status == PrescriptionItemStatus.CANCELLED;
                });
        if (allRefundedOrCancelled) {
            prescription.setStatus(PrescriptionStatus.CANCELLED);
        } else if (prescription.getStatus() != PrescriptionStatus.DISPENSED
                && prescription.getStatus() != PrescriptionStatus.CANCELLED) {
            prescription.setStatus(PrescriptionStatus.PAID);
        }
        prescriptionRepository.save(prescription);
    }

    private boolean allInvoiceItemsRefunded(Invoice invoice) {
        return invoice.getItems() != null
                && !invoice.getItems().isEmpty()
                && invoice.getItems().stream().allMatch(this::isInvoiceItemRefunded);
    }

    private boolean isInvoiceItemRefunded(InvoiceItem item) {
        long totalAmount = item.getTotalAmount() != null ? item.getTotalAmount() : 0L;
        long refundedAmount = item.getRefundedAmount() != null ? item.getRefundedAmount() : 0L;
        return totalAmount > 0 && refundedAmount >= totalAmount;
    }

    private long refundedAmount(Invoice invoice) {
        long itemRefundedAmount = invoice.getItems() != null
                ? invoice.getItems().stream()
                .mapToLong(item -> item.getRefundedAmount() != null ? item.getRefundedAmount() : 0L)
                .sum()
                : 0L;
        if (itemRefundedAmount == 0L && invoice.getPaymentStatus() == PaymentStatus.REFUNDED) {
            return invoice.getTotalAmount() != null ? invoice.getTotalAmount() : 0L;
        }
        return itemRefundedAmount;
    }

    private long remainingAmount(Invoice invoice, long refundedAmount) {
        long totalAmount = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : 0L;
        return Math.max(0L, totalAmount - refundedAmount);
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

        intent.setProvider(PaymentIntentProvider.VIETQR);
        intent.setPaymentReference(invoice.getPaymentReference());
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
        if (status == PaymentIntentStatus.PENDING) {
            intent.setDetectedAt(null);
            intent.setConfirmedAt(null);
            intent.setMatchedTransactionRef(null);
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
        if (invoice.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || invoice.getPaymentStatus() == PaymentStatus.REFUNDED
                || invoice.getPaymentStatus() == PaymentStatus.VOID) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Hóa đơn đã hoàn/hủy, không thể xác nhận thanh toán");
        }

        if (invoice.getPrescription() != null) {
            applyPrescriptionPaidState(invoice, paidAt);
            return;
        }

        if (invoice.getServiceOrder() == null) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Hóa đơn không liên kết phiếu chỉ định hoặc đơn thuốc");
        }

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

    private void applyPrescriptionPaidState(Invoice invoice, LocalDateTime paidAt) {
        Prescription prescription = invoice.getPrescription();
        if (prescription.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Đơn thuốc đã hủy, không thể thanh toán");
        }
        if (prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Đơn thuốc đã phát, không thể thanh toán lại");
        }
        if (prescription.getStatus() != PrescriptionStatus.ISSUED
                && prescription.getStatus() != PrescriptionStatus.PAID) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Chỉ thanh toán đơn thuốc đã phát hành");
        }

        LocalDateTime now = paidAt != null ? paidAt : LocalDateTime.now();
        invoice.setPaymentStatus(PaymentStatus.PAID);
        invoice.setPaidAt(now);
        prescription.setStatus(PrescriptionStatus.PAID);
        if (prescription.getItems() != null) {
            for (PrescriptionItem item : prescription.getItems()) {
                PrescriptionItemStatus status = effectivePrescriptionItemStatus(item);
                if (status == PrescriptionItemStatus.ISSUED || status == PrescriptionItemStatus.PAID) {
                    item.setStatus(PrescriptionItemStatus.PAID);
                }
            }
        }
    }

    private void publishPaidRealtime(Invoice invoice) {
        if (invoice.getPrescription() != null) {
            publishPrescriptionPaidRealtime(invoice);
            return;
        }

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

    private void publishPrescriptionPaidRealtime(Invoice invoice) {
        Prescription prescription = invoice.getPrescription();
        Long encounterId = prescription.getEncounter() != null ? prescription.getEncounter().getId() : null;
        Long doctorProfileId = prescription.getEncounter() != null && prescription.getEncounter().getDoctor() != null
                ? prescription.getEncounter().getDoctor().getId()
                : null;
        Long branchId = prescription.getEncounter() != null && prescription.getEncounter().getBranch() != null
                ? prescription.getEncounter().getBranch().getId()
                : null;
        String encounterStatus = prescription.getEncounter() != null && prescription.getEncounter().getStatus() != null
                ? prescription.getEncounter().getStatus().name()
                : null;
        Long invoiceId = invoice.getId();
        String invoiceCode = invoice.getCode();
        String prescriptionCode = prescription.getCode();
        String patientName = prescription.getEncounter() != null ? prescription.getEncounter().getPatientFullNameSnapshot() : null;
        Long amount = invoice.getTotalAmount();

        afterCommitExecutor.execute(() -> {
            if (encounterId != null) {
                realtimeEventPublisher.publishEncounterChannel(
                        encounterId,
                        "PRESCRIPTION_PAID",
                        Map.of(
                                "prescriptionId", prescription.getId(),
                                "prescriptionCode", prescriptionCode,
                                "invoiceId", invoiceId,
                                "invoiceCode", invoiceCode
                        )
                );
            }
            if (doctorProfileId != null && encounterId != null && encounterStatus != null) {
                realtimeEventPublisher.publishDoctorEncounterUpdated(doctorProfileId, encounterId, encounterStatus);
            }
            realtimeEventPublisher.publishCashierOrderEvent(
                    branchId,
                    "PRESCRIPTION_INVOICE_PAID",
                    null,
                    prescriptionCode,
                    patientName,
                    amount,
                    invoiceId,
                    invoiceCode
            );
            internalNotificationService.notifyRole(
                    UserRole.PHARMACIST,
                    "PRESCRIPTION_PAID",
                    InternalNotificationService.SEVERITY_INFO,
                    "Có đơn thuốc chờ phát",
                    "Đơn thuốc " + prescriptionCode + " đã thanh toán và chờ phát thuốc.",
                    "/app/pharmacy/dispense",
                    "PRESCRIPTION",
                    prescription.getId()
            );
        });
    }

    private void publishInvoiceCreatedRealtime(Invoice invoice) {
        if (invoice.getPrescription() != null) {
            Prescription prescription = invoice.getPrescription();
            Long branchId = prescription.getEncounter() != null && prescription.getEncounter().getBranch() != null
                    ? prescription.getEncounter().getBranch().getId()
                    : null;
            String patientName = prescription.getEncounter() != null ? prescription.getEncounter().getPatientFullNameSnapshot() : null;
            afterCommitExecutor.execute(() -> realtimeEventPublisher.publishCashierOrderEvent(
                    branchId,
                    "PRESCRIPTION_INVOICE_CREATED",
                    null,
                    prescription.getCode(),
                    patientName,
                    invoice.getTotalAmount(),
                    invoice.getId(),
                    invoice.getCode()
            ));
            return;
        }

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

    private void publishInvoicePaymentMethodChangedRealtime(Invoice invoice) {
        if (invoice.getPrescription() != null) {
            Prescription prescription = invoice.getPrescription();
            Long branchId = prescription.getEncounter() != null && prescription.getEncounter().getBranch() != null
                    ? prescription.getEncounter().getBranch().getId()
                    : null;
            String patientName = prescription.getEncounter() != null
                    ? prescription.getEncounter().getPatientFullNameSnapshot()
                    : null;
            afterCommitExecutor.execute(() -> realtimeEventPublisher.publishCashierOrderEvent(
                    branchId,
                    "PRESCRIPTION_INVOICE_PAYMENT_METHOD_CHANGED",
                    null,
                    prescription.getCode(),
                    patientName,
                    invoice.getTotalAmount(),
                    invoice.getId(),
                    invoice.getCode()
            ));
            return;
        }

        ServiceOrder order = invoice.getServiceOrder();
        Long branchId = order != null && order.getBranch() != null ? order.getBranch().getId() : null;
        afterCommitExecutor.execute(() -> realtimeEventPublisher.publishCashierOrderEvent(
                branchId,
                "INVOICE_PAYMENT_METHOD_CHANGED",
                order != null ? order.getId() : null,
                order != null ? order.getCode() : null,
                order != null && order.getEncounter() != null ? order.getEncounter().getPatientFullNameSnapshot() : null,
                invoice.getTotalAmount(),
                invoice.getId(),
                invoice.getCode()
        ));
    }

    private void publishInvoiceItemsRefundedRealtime(Invoice invoice) {
        if (invoice.getPrescription() != null) {
            Prescription prescription = invoice.getPrescription();
            Long encounterId = prescription.getEncounter() != null ? prescription.getEncounter().getId() : null;
            Long branchId = prescription.getEncounter() != null && prescription.getEncounter().getBranch() != null
                    ? prescription.getEncounter().getBranch().getId()
                    : null;
            String patientName = prescription.getEncounter() != null
                    ? prescription.getEncounter().getPatientFullNameSnapshot()
                    : null;
            afterCommitExecutor.execute(() -> {
                if (encounterId != null) {
                    realtimeEventPublisher.publishEncounterChannel(
                            encounterId,
                            "PRESCRIPTION_ITEMS_REFUNDED",
                            Map.of(
                                    "prescriptionId", prescription.getId(),
                                    "prescriptionCode", prescription.getCode(),
                                    "invoiceId", invoice.getId(),
                                    "invoiceCode", invoice.getCode()
                            )
                    );
                }
                realtimeEventPublisher.publishCashierOrderEvent(
                        branchId,
                        "PRESCRIPTION_INVOICE_ITEMS_REFUNDED",
                        null,
                        prescription.getCode(),
                        patientName,
                        invoice.getTotalAmount(),
                        invoice.getId(),
                        invoice.getCode()
                );
            });
            return;
        }

        ServiceOrder order = invoice.getServiceOrder();
        Long branchId = order != null && order.getBranch() != null ? order.getBranch().getId() : null;
        Long encounterId = order != null && order.getEncounter() != null ? order.getEncounter().getId() : null;
        String encounterStatus = order != null && order.getEncounter() != null && order.getEncounter().getStatus() != null
                ? order.getEncounter().getStatus().name()
                : null;
        afterCommitExecutor.execute(() -> {
            if (encounterId != null && encounterStatus != null) {
                realtimeEventPublisher.publishEncounterChannel(
                        encounterId,
                        "SERVICE_ORDER_ITEMS_REFUNDED",
                        Map.of(
                                "status", encounterStatus,
                                "invoiceId", invoice.getId(),
                                "invoiceCode", invoice.getCode()
                        )
                );
            }
            realtimeEventPublisher.publishCashierOrderEvent(
                    branchId,
                    "INVOICE_ITEMS_REFUNDED",
                    order != null ? order.getId() : null,
                    order != null ? order.getCode() : null,
                    order != null && order.getEncounter() != null ? order.getEncounter().getPatientFullNameSnapshot() : null,
                    invoice.getTotalAmount(),
                    invoice.getId(),
                    invoice.getCode()
            );
        });
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

    private Optional<LocalDateTime> parseVnpayPayDate(String payDate) {
        if (payDate == null || payDate.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(payDate, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        } catch (Exception ignored) {
            return Optional.empty();
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

    private String generatePrescriptionInvoiceCode(Prescription prescription) {
        int random = secureRandom.nextInt(10000);
        return "INV-RX-%d-%04d-%04d".formatted(
                System.currentTimeMillis(),
                prescription.getId() % 10000,
                random
        );
    }

    private void validatePrescriptionInvoiceSource(Prescription prescription) {
        if (prescription.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Đơn thuốc đã hủy, không thể tạo hóa đơn");
        }
        if (prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Đơn thuốc đã phát, không thể tạo hóa đơn mới");
        }
        if (prescription.getStatus() != PrescriptionStatus.ISSUED
                && prescription.getStatus() != PrescriptionStatus.PAID) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Chỉ tạo hóa đơn cho đơn thuốc đã phát hành");
        }
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

        ServiceOrder serviceOrder = entity.getServiceOrder();
        Prescription prescription = entity.getPrescription();
        var encounter = serviceOrder != null
                ? serviceOrder.getEncounter()
                : (prescription != null ? prescription.getEncounter() : null);
        var branch = serviceOrder != null
                ? serviceOrder.getBranch()
                : (encounter != null ? encounter.getBranch() : null);
        long refundedAmount = refundedAmount(entity);

        return InvoiceResponse.builder()
                              .id(entity.getId())
                              .code(entity.getCode())
                              .invoiceType(prescription != null ? "PRESCRIPTION" : "SERVICE_ORDER")
                              .serviceOrderId(serviceOrder != null ? serviceOrder.getId() : null)
                              .serviceOrderCode(serviceOrder != null ? serviceOrder.getCode() : null)
                              .prescriptionId(prescription != null ? prescription.getId() : null)
                              .prescriptionCode(prescription != null ? prescription.getCode() : null)
                              .encounterId(encounter != null ? encounter.getId() : null)
                              .patientName(resolveInvoicePatientName(serviceOrder, prescription))
                              .doctorName(encounter != null && encounter.getDoctor() != null ? encounter.getDoctor().getFullName() : null)
                              .branchName(branch != null ? branch.getNameVn() : null)
                              .subtotalAmount(entity.getSubtotalAmount())
                              .discountAmount(entity.getDiscountAmount())
                              .taxAmount(entity.getTaxAmount())
                              .totalAmount(entity.getTotalAmount())
                              .refundedAmount(refundedAmount)
                              .remainingAmount(remainingAmount(entity, refundedAmount))
                              .items(entity.getItems() != null ? entity.getItems().stream().map(i ->
                                      com.PrimeCare.PrimeCare.modules.billing.dto.response.InvoiceItemResponse.builder()
                                              .id(i.getId())
                                              .referenceType(i.getReferenceType().name())
                                              .referenceId(i.getReferenceId())
                                              .sourceItemType(i.getSourceItemType() != null ? i.getSourceItemType().name() : null)
                                              .sourceItemId(i.getSourceItemId())
                                              .nameSnapshot(i.getNameSnapshot())
                                              .unitPrice(i.getUnitPrice())
                                              .quantity(i.getQuantity())
                                              .taxRate(i.getTaxRate())
                                              .subtotalAmount(i.getSubtotalAmount())
                                              .taxAmount(i.getTaxAmount())
                                              .totalAmount(i.getTotalAmount())
                                              .refundedAmount(i.getRefundedAmount() != null ? i.getRefundedAmount() : 0L)
                                              .refundStatus(i.getRefundStatus() != null ? i.getRefundStatus().name() : null)
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
                                                                          .cancelledAt(i.getCancelledAt())
                                                                          .refundReason(i.getRefundReason())
                                                                          .refundedAt(i.getRefundedAt())
                                                                          .build()
                                          ).toList())
                                          .build();
    }

    private String resolveInvoicePatientName(ServiceOrder serviceOrder, Prescription prescription) {
        if (serviceOrder != null && serviceOrder.getEncounter() != null) {
            if (serviceOrder.getEncounter().getPatient() != null
                    && serviceOrder.getEncounter().getPatient().getFullName() != null) {
                return serviceOrder.getEncounter().getPatient().getFullName();
            }
            return serviceOrder.getEncounter().getPatientFullNameSnapshot();
        }
        if (prescription != null && prescription.getEncounter() != null) {
            if (prescription.getEncounter().getPatient() != null
                    && prescription.getEncounter().getPatient().getFullName() != null) {
                return prescription.getEncounter().getPatient().getFullName();
            }
            return prescription.getEncounter().getPatientFullNameSnapshot();
        }
        return null;
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
        data.put("prescriptionId", invoice.getPrescription() != null ? invoice.getPrescription().getId() : null);
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
        data.put("hasVnpPaymentUrl", invoice.getVnpPaymentUrl() != null && !invoice.getVnpPaymentUrl().isBlank());
        return data;
    }

    private Map<String, Object> snapshotPaymentMethodChangeBefore(Invoice invoice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", invoice.getId());
        data.put("invoiceCode", invoice.getCode());
        data.put("oldPaymentMethod", invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : null);
        data.put("oldPaymentStatus", invoice.getPaymentStatus() != null ? invoice.getPaymentStatus().name() : null);
        data.put("oldPaymentReference", invoice.getPaymentReference());
        data.put("oldTransferContent", invoice.getTransferContent());
        data.put("oldVnpTxnRef", invoice.getVnpTxnRef());
        data.put("oldVnpPaymentUrl", invoice.getVnpPaymentUrl());
        return data;
    }

    private Map<String, Object> snapshotPaymentMethodChangeAfter(Invoice invoice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", invoice.getId());
        data.put("invoiceCode", invoice.getCode());
        data.put("newPaymentMethod", invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : null);
        data.put("newPaymentStatus", invoice.getPaymentStatus() != null ? invoice.getPaymentStatus().name() : null);
        data.put("newPaymentReference", invoice.getPaymentReference());
        data.put("newTransferContent", invoice.getTransferContent());
        data.put("newVnpTxnRef", invoice.getVnpTxnRef());
        data.put("newVnpPaymentUrl", invoice.getVnpPaymentUrl());
        return data;
    }

    private Map<String, Object> snapshotInvoiceItemRefund(Invoice invoice, List<RefundableEvaluation> evaluations) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", invoice.getId());
        data.put("invoiceCode", invoice.getCode());
        data.put("paymentStatus", invoice.getPaymentStatus() != null ? invoice.getPaymentStatus().name() : null);
        data.put("totalAmount", invoice.getTotalAmount());
        data.put("refundedAmount", refundedAmount(invoice));
        data.put("selectedItems", evaluations.stream().map(this::snapshotRefundEvaluation).toList());
        return data;
    }

    private Map<String, Object> snapshotInvoiceItemRefundAfter(
            Invoice invoice,
            List<RefundableEvaluation> evaluations,
            String reason
    ) {
        long refundedAmount = refundedAmount(invoice);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", invoice.getId());
        data.put("invoiceCode", invoice.getCode());
        data.put("paymentStatus", invoice.getPaymentStatus() != null ? invoice.getPaymentStatus().name() : null);
        data.put("totalAmount", invoice.getTotalAmount());
        data.put("refundedAmount", refundedAmount);
        data.put("remainingAmount", remainingAmount(invoice, refundedAmount));
        data.put("refundedInvoiceItemIds", evaluations.stream().map(e -> e.invoiceItem().getId()).toList());
        data.put("items", evaluations.stream().map(this::snapshotRefundEvaluation).toList());
        data.put("reason", reason);
        return data;
    }

    private Map<String, Object> snapshotRefundEvaluation(RefundableEvaluation evaluation) {
        Map<String, Object> data = new LinkedHashMap<>();
        InvoiceItem item = evaluation.invoiceItem();
        data.put("invoiceItemId", item.getId());
        data.put("sourceItemType", item.getSourceItemType() != null ? item.getSourceItemType().name() : null);
        data.put("sourceItemId", item.getSourceItemId());
        data.put("name", item.getNameSnapshot());
        data.put("totalAmount", item.getTotalAmount());
        data.put("refundedAmount", item.getRefundedAmount());
        if (evaluation.serviceOrderItem() != null) {
            data.put("serviceOrderItemStatus", evaluation.serviceOrderItem().getStatus() != null
                    ? evaluation.serviceOrderItem().getStatus().name()
                    : null);
            data.put("serviceResultStatus", evaluation.serviceOrderItem().getResultStatus() != null
                    ? evaluation.serviceOrderItem().getResultStatus().name()
                    : null);
        }
        if (evaluation.prescriptionItem() != null) {
            data.put("prescriptionItemStatus", evaluation.prescriptionItem().getStatus() != null
                    ? evaluation.prescriptionItem().getStatus().name()
                    : null);
        }
        return data;
    }

    @Transactional
    public InvoiceResponse refundInvoice(Long invoiceId, Long refundAmount, String reason, Long cashierUserId) {
        Invoice invoice = invoiceRepository.findWithLockById(invoiceId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));

        if (!isRefundableInvoiceStatus(invoice.getPaymentStatus())) {
            if (invoice.getPaymentStatus() == PaymentStatus.REFUNDED) {
                throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, "Hóa đơn đã được hoàn tiền toàn bộ");
            }
            throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, "Chỉ có thể hoàn tiền cho hóa đơn đã thanh toán");
        }

        String normalizedReason = optionalText(reason, null);
        if (normalizedReason == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Lý do hoàn tiền không được để trống");
        }

        long remainingAmount = remainingAmount(invoice, refundedAmount(invoice));
        Long effectiveRefundAmount = refundAmount != null ? refundAmount : remainingAmount;
        if (effectiveRefundAmount == null || effectiveRefundAmount <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Số tiền hoàn phải lớn hơn 0");
        }

        if (!effectiveRefundAmount.equals(remainingAmount)) {
            throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, "Hệ thống hiện chỉ hỗ trợ hoàn toàn bộ hóa đơn");
        }

        if (invoice.getItems() != null && !invoice.getItems().isEmpty()) {
            Set<Long> remainingItemIds = invoice.getItems().stream()
                    .filter(item -> !isInvoiceItemRefunded(item))
                    .map(InvoiceItem::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (remainingItemIds.isEmpty()) {
                throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, "Hóa đơn đã được hoàn tiền toàn bộ");
            }
            return refundInvoiceItemsInternal(invoice, remainingItemIds, normalizedReason, cashierUserId, "Full invoice item refund");
        }

        User cashier = userRepository.findById(cashierUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        Map<String, Object> before = snapshotInvoice(invoice);
        var previousPaymentStatus = invoice.getPaymentStatus();

        RefundRecord refund = RefundRecord.builder()
                .invoice(invoice)
                .refundAmount(effectiveRefundAmount)
                .reason(normalizedReason)
                .approvedByUser(cashier)
                .build();
        refundRecordRepository.save(refund);

        RefundCascadeResult cascadeResult = applyRefundedState(invoice);
        invoice.setPaymentStatus(PaymentStatus.REFUNDED);
        Invoice saved = invoiceRepository.save(invoice);

        invoiceStatusHistoryService.record(saved, previousPaymentStatus, saved.getPaymentStatus(), cashier, "Refund: " + normalizedReason);
        auditLogService.log(cashier, "REFUND_INVOICE", "INVOICE", saved.getId(), before, snapshotInvoice(saved));
        publishRefundRealtime(saved, cascadeResult);

        return toResponse(saved, true);
    }

    @Transactional
    public InvoiceResponse createPrescriptionInvoice(Long prescriptionId, Long cashierUserId, PayInvoiceRequest req) {
        Prescription prescription = prescriptionRepository.findWithLockDetailsById(prescriptionId)
                .orElseThrow(() -> new ApiException(ErrorCode.PRESCRIPTION_NOT_FOUND));

        validatePrescriptionInvoiceSource(prescription);

        Optional<Invoice> existingInvoice = invoiceRepository.findByPrescription_Id(prescription.getId());
        if (existingInvoice.isPresent()) {
            return toResponse(existingInvoice.get(), true);
        }

        User cashier = userRepository.findById(cashierUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        long totalSubtotal = 0L;
        List<InvoiceItem> invoiceItems = new ArrayList<>();

        for (PrescriptionItem prescriptionItem : prescription.getItems()) {
            if (prescriptionItem.getQuantity() == null || prescriptionItem.getQuantity() <= 0) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Số lượng thuốc trong đơn không hợp lệ");
            }
            Long unitPrice = prescriptionItem.getMedication() != null
                    ? prescriptionItem.getMedication().getUnitPrice()
                    : null;
            if (unitPrice == null || unitPrice < 0) {
                throw new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "Thuốc " + prescriptionItem.getMedicationNameSnapshot() + " chưa có đơn giá bán"
                );
            }

            long itemSubtotal = unitPrice * prescriptionItem.getQuantity();
            totalSubtotal += itemSubtotal;

            invoiceItems.add(InvoiceItem.builder()
                    .referenceType(InvoiceItem.ReferenceType.MEDICATION)
                    .referenceId(prescriptionItem.getMedication() != null ? prescriptionItem.getMedication().getId() : null)
                    .sourceItemType(InvoiceItemSourceType.PRESCRIPTION_ITEM)
                    .sourceItemId(prescriptionItem.getId())
                    .nameSnapshot(prescriptionItem.getMedicationNameSnapshot())
                    .unitPrice(unitPrice)
                    .quantity(prescriptionItem.getQuantity())
                    .taxRate(BigDecimal.ZERO)
                    .subtotalAmount(itemSubtotal)
                    .taxAmount(0L)
                    .totalAmount(itemSubtotal)
                    .build());
        }

        if (invoiceItems.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Đơn thuốc phải có ít nhất 1 thuốc để tạo hóa đơn");
        }

        PaymentStatus initialPaymentStatus = req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                ? PaymentStatus.PENDING_CONFIRMATION
                : PaymentStatus.UNPAID;

        Invoice invoice = Invoice.builder()
                .code(generatePrescriptionInvoiceCode(prescription))
                .prescription(prescription)
                .cashier(cashier)
                .subtotalAmount(totalSubtotal)
                .discountAmount(0L)
                .taxAmount(0L)
                .totalAmount(totalSubtotal)
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

        invoiceStatusHistoryService.record(saved, null, saved.getPaymentStatus(), cashier, "Prescription invoice created");
        auditLogService.log(cashier, "CREATE_INVOICE", "INVOICE", saved.getId(), null, snapshotInvoice(saved));
        publishInvoiceCreatedRealtime(saved);

        return toResponse(saved, true);
    }

    private RefundCascadeResult applyRefundedState(Invoice invoice) {
        if (invoice.getPrescription() != null) {
            Prescription prescription = invoice.getPrescription();
            if (prescription.getStatus() == PrescriptionStatus.DISPENSED) {
                throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, "Đơn thuốc đã phát, không thể hoàn tiền hóa đơn thuốc");
            }
            if (prescription.getItems() != null) {
                for (PrescriptionItem item : prescription.getItems()) {
                    String reason = prescriptionItemNotRefundableReason(item);
                    if (reason != null) {
                        throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, reason);
                    }
                    item.setStatus(PrescriptionItemStatus.REFUNDED);
                    item.setRefundedAt(LocalDateTime.now());
                }
            }
            if (prescription.getStatus() == PrescriptionStatus.PAID) {
                prescription.setStatus(PrescriptionStatus.CANCELLED);
            }
            return new RefundCascadeResult(null, null, null);
        }

        if (invoice.getServiceOrder() == null) {
            throw new ApiException(ErrorCode.INVOICE_INVALID_STATUS, "Hóa đơn không liên kết phiếu chỉ định hoặc đơn thuốc");
        }

        ServiceOrder order = orderRepository.findWithLockById(invoice.getServiceOrder().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_ORDER_NOT_FOUND));

        validateLegacyServiceOrderFullRefund(order);

        LocalDateTime now = LocalDateTime.now();

        order.setPaymentStatus(PaymentStatus.REFUNDED);
        order.setStatus(ServiceOrderStatus.CANCELLED);
        order.setCancelledAt(now);

        for (ServiceOrderItem item : order.getItems()) {
            item.setStatus(ServiceOrderItemStatus.CANCELLED);
            item.setRefundedAt(now);
            if (item.getCancelledAt() == null) {
                item.setCancelledAt(now);
            }
        }

        EncounterStatus previousEncounterStatus = null;
        AppointmentStatus previousAppointmentStatus = null;
        if (order.getEncounter() != null) {
            previousEncounterStatus = order.getEncounter().getStatus();
            if (previousEncounterStatus != EncounterStatus.CANCELLED
                    && previousEncounterStatus != EncounterStatus.COMPLETED) {
                EncounterStatus nextEncounterStatus = encounterWorkflowService.resolveStatus(order.getEncounter());
                order.getEncounter().setStatus(nextEncounterStatus);
            }
        }

        return new RefundCascadeResult(order, previousEncounterStatus, previousAppointmentStatus);
    }

    private void validateLegacyServiceOrderFullRefund(ServiceOrder order) {
        if (order.getItems() == null) {
            return;
        }
        for (ServiceOrderItem item : order.getItems()) {
            ServiceResult result = item.getId() != null
                    ? serviceResultRepository.findByServiceOrderItem_Id(item.getId()).orElse(null)
                    : null;
            String reason = serviceItemNotRefundableReason(item, result);
            if (reason != null) {
                throw new ApiException(ErrorCode.REFUND_NOT_ALLOWED, reason);
            }
        }
    }

    private void publishRefundRealtime(Invoice invoice, RefundCascadeResult cascadeResult) {
        ServiceOrder order = cascadeResult.order();
        if (order == null && invoice.getPrescription() != null) {
            publishPrescriptionRefundRealtime(invoice);
            return;
        }

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

    private void publishPrescriptionRefundRealtime(Invoice invoice) {
        Prescription prescription = invoice.getPrescription();
        Long encounterId = prescription.getEncounter() != null ? prescription.getEncounter().getId() : null;
        Long branchId = prescription.getEncounter() != null && prescription.getEncounter().getBranch() != null
                ? prescription.getEncounter().getBranch().getId()
                : null;
        String patientName = prescription.getEncounter() != null ? prescription.getEncounter().getPatientFullNameSnapshot() : null;
        Long invoiceId = invoice.getId();
        String invoiceCode = invoice.getCode();

        afterCommitExecutor.execute(() -> {
            if (encounterId != null) {
                realtimeEventPublisher.publishEncounterChannel(
                        encounterId,
                        "PRESCRIPTION_PAYMENT_REFUNDED",
                        Map.of(
                                "prescriptionId", prescription.getId(),
                                "prescriptionCode", prescription.getCode(),
                                "invoiceId", invoiceId,
                                "invoiceCode", invoiceCode
                        )
                );
            }
            realtimeEventPublisher.publishCashierOrderEvent(
                    branchId,
                    "PRESCRIPTION_INVOICE_REFUNDED",
                    null,
                    prescription.getCode(),
                    patientName,
                    invoice.getTotalAmount(),
                    invoiceId,
                    invoiceCode
            );
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

    private record RefundableEvaluation(
            Invoice invoice,
            InvoiceItem invoiceItem,
            ServiceOrderItem serviceOrderItem,
            ServiceResult serviceResult,
            PrescriptionItem prescriptionItem,
            long alreadyRefundedAmount,
            long refundableAmount,
            boolean refundable,
            String notRefundableReason
    ) {
        static RefundableEvaluation refundable(
                Invoice invoice,
                InvoiceItem invoiceItem,
                ServiceOrderItem serviceOrderItem,
                ServiceResult serviceResult,
                long alreadyRefundedAmount,
                long refundableAmount
        ) {
            return new RefundableEvaluation(
                    invoice,
                    invoiceItem,
                    serviceOrderItem,
                    serviceResult,
                    null,
                    alreadyRefundedAmount,
                    refundableAmount,
                    true,
                    null
            );
        }

        static RefundableEvaluation refundable(
                Invoice invoice,
                InvoiceItem invoiceItem,
                PrescriptionItem prescriptionItem,
                long alreadyRefundedAmount,
                long refundableAmount
        ) {
            return new RefundableEvaluation(
                    invoice,
                    invoiceItem,
                    null,
                    null,
                    prescriptionItem,
                    alreadyRefundedAmount,
                    refundableAmount,
                    true,
                    null
            );
        }

        static RefundableEvaluation notRefundable(
                Invoice invoice,
                InvoiceItem invoiceItem,
                long alreadyRefundedAmount,
                long refundableAmount,
                String reason
        ) {
            return new RefundableEvaluation(
                    invoice,
                    invoiceItem,
                    null,
                    null,
                    null,
                    alreadyRefundedAmount,
                    refundableAmount,
                    false,
                    reason
            );
        }

        static RefundableEvaluation notRefundable(
                Invoice invoice,
                InvoiceItem invoiceItem,
                ServiceOrderItem serviceOrderItem,
                ServiceResult serviceResult,
                long alreadyRefundedAmount,
                long refundableAmount,
                String reason
        ) {
            return new RefundableEvaluation(
                    invoice,
                    invoiceItem,
                    serviceOrderItem,
                    serviceResult,
                    null,
                    alreadyRefundedAmount,
                    refundableAmount,
                    false,
                    reason
            );
        }

        static RefundableEvaluation notRefundable(
                Invoice invoice,
                InvoiceItem invoiceItem,
                PrescriptionItem prescriptionItem,
                long alreadyRefundedAmount,
                long refundableAmount,
                String reason
        ) {
            return new RefundableEvaluation(
                    invoice,
                    invoiceItem,
                    null,
                    null,
                    prescriptionItem,
                    alreadyRefundedAmount,
                    refundableAmount,
                    false,
                    reason
            );
        }

        RefundableInvoiceItemResponse toResponse() {
            String currentStatus = null;
            String resultStatus = null;
            String group = invoiceItem.getReferenceType() == InvoiceItem.ReferenceType.MEDICATION
                    ? "MEDICATION"
                    : "SERVICE";
            if (serviceOrderItem != null) {
                currentStatus = serviceOrderItem.getStatus() != null ? serviceOrderItem.getStatus().name() : null;
                resultStatus = serviceOrderItem.getResultStatus() != null ? serviceOrderItem.getResultStatus().name() : null;
            } else if (prescriptionItem != null) {
                currentStatus = prescriptionItem.getStatus() != null ? prescriptionItem.getStatus().name() : null;
            }

            return RefundableInvoiceItemResponse.builder()
                    .invoiceItemId(invoiceItem.getId())
                    .sourceItemType(invoiceItem.getSourceItemType() != null ? invoiceItem.getSourceItemType().name() : null)
                    .sourceItemId(invoiceItem.getSourceItemId())
                    .referenceType(invoiceItem.getReferenceType() != null ? invoiceItem.getReferenceType().name() : null)
                    .name(invoiceItem.getNameSnapshot())
                    .quantity(invoiceItem.getQuantity())
                    .totalAmount(invoiceItem.getTotalAmount())
                    .alreadyRefundedAmount(alreadyRefundedAmount)
                    .refundableAmount(refundable ? refundableAmount : 0L)
                    .refundable(refundable)
                    .notRefundableReason(notRefundableReason)
                    .currentStatus(currentStatus)
                    .resultStatus(resultStatus)
                    .group(group)
                    .build();
        }
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
