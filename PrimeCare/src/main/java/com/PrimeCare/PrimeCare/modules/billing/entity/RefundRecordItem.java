package com.PrimeCare.PrimeCare.modules.billing.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.InvoiceItemSourceType;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refund_record_items")
public class RefundRecordItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_record_id", nullable = false)
    private RefundRecord refundRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_item_id", nullable = false)
    private InvoiceItem invoiceItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_item_type", length = 32)
    private InvoiceItemSourceType sourceItemType;

    @Column(name = "source_item_id")
    private Long sourceItemId;

    @Column(name = "name_snapshot", nullable = false, length = 255)
    private String nameSnapshot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount;
}
