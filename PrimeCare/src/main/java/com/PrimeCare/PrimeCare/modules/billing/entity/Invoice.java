package com.PrimeCare.PrimeCare.modules.billing.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.PaymentMethod;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_invoice_code", columnNames = "code"),
                @UniqueConstraint(name = "uq_invoice_service_order", columnNames = "service_order_id"),
                @UniqueConstraint(name = "uq_invoice_prescription", columnNames = "prescription_id")
        }
)
// Monetary amounts are stored as whole VND dong. Do not treat these Long fields as decimal major units.
public class Invoice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_order_id", unique = true)
    private ServiceOrder serviceOrder;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", unique = true)
    private Prescription prescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id")
    private User cashier;

    @Column(name = "subtotal_amount", nullable = false)
    private Long subtotalAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "tax_amount", nullable = false)
    private Long taxAmount;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<InvoiceItem> items;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 16)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    private PaymentStatus paymentStatus;

    @Column(name = "payment_reference", length = 32)
    private String paymentReference;

    @Column(name = "transfer_content", length = 64)
    private String transferContent;

    @Column(name = "payment_detected_at")
    private LocalDateTime paymentDetectedAt;

    @Column(name = "payment_review_reason", length = 255)
    private String paymentReviewReason;

    @Column(name = "vnp_txn_ref", length = 64)
    private String vnpTxnRef;

    @Column(name = "vnp_payment_url", length = 2000)
    private String vnpPaymentUrl;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "note", length = 500)
    private String note;

    @PrePersist
    void prePersist() {
        if (paymentStatus == null) paymentStatus = PaymentStatus.UNPAID;
        if (discountAmount == null) discountAmount = 0L;
    }
}
