package com.PrimeCare.PrimeCare.modules.billing.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.PaymentIntentProvider;
import com.PrimeCare.PrimeCare.shared.enums.PaymentIntentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_intents",
       uniqueConstraints = {
               @UniqueConstraint(name = "uq_payment_intent_invoice", columnNames = "invoice_id"),
               @UniqueConstraint(name = "uq_payment_intent_reference", columnNames = "payment_reference")
       })
public class PaymentIntent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false, unique = true)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private PaymentIntentProvider provider;

    @Column(name = "payment_reference", nullable = false, length = 32)
    private String paymentReference;

    @Column(name = "transfer_content", nullable = false, length = 64)
    private String transferContent;

    @Column(name = "bank_code", length = 32)
    private String bankCode;

    @Column(name = "bank_account_no", length = 64)
    private String bankAccountNo;

    @Column(name = "bank_account_name", length = 255)
    private String bankAccountName;

    @Column(name = "expected_amount", nullable = false)
    private Long expectedAmount;

    @Column(name = "qr_payload", columnDefinition = "TEXT")
    private String qrPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PaymentIntentStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "matched_transaction_ref", length = 128)
    private String matchedTransactionRef;

    @Column(name = "review_reason", length = 255)
    private String reviewReason;

    @Column(name = "note", length = 500)
    private String note;
}
