package com.PrimeCare.PrimeCare.modules.billing.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.BankTransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bank_transactions",
       uniqueConstraints = {
               @UniqueConstraint(name = "uq_bank_transaction_ref", columnNames = "transaction_ref")
       })
public class BankTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "transaction_ref", nullable = false, length = 128)
    private String transactionRef;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "transfer_content", length = 255)
    private String transferContent;

    @Column(name = "bank_account_no", length = 64)
    private String bankAccountNo;

    @Column(name = "bank_code", length = 32)
    private String bankCode;

    @Column(name = "transaction_time")
    private LocalDateTime transactionTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private BankTransactionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_invoice_id")
    private Invoice matchedInvoice;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    @Column(name = "review_reason", length = 255)
    private String reviewReason;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;
}
