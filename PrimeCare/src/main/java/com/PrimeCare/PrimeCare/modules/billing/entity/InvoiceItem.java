package com.PrimeCare.PrimeCare.modules.billing.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.InvoiceItemRefundStatus;
import com.PrimeCare.PrimeCare.shared.enums.InvoiceItemSourceType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "invoice_items")
public class InvoiceItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 32)
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_item_type", length = 32)
    private InvoiceItemSourceType sourceItemType;

    @Column(name = "source_item_id")
    private Long sourceItemId;

    @Column(name = "name_snapshot", nullable = false, length = 255)
    private String nameSnapshot;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate; // Ví dụ: 0.10 (10%), 0.08 (8%)

    @Column(name = "subtotal_amount", nullable = false)
    private Long subtotalAmount; // Trước thuế

    @Column(name = "tax_amount", nullable = false)
    private Long taxAmount; // Tiền thuế

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount; // Sau thuế

    @Column(name = "refunded_amount", nullable = false)
    @Builder.Default
    private Long refundedAmount = 0L;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", length = 32)
    private InvoiceItemRefundStatus refundStatus;

    @PrePersist
    void prePersist() {
        if (refundedAmount == null) refundedAmount = 0L;
    }

    public enum ReferenceType {
        CLINICAL_SERVICE,
        MEDICATION,
        OTHER
    }
}
