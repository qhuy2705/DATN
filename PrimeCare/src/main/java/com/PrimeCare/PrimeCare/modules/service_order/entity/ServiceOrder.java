package com.PrimeCare.PrimeCare.modules.service_order.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "service_orders")
public class ServiceOrder extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ordered_by_doctor_id", nullable = false)
    private User orderedByDoctor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32)
    private ServiceOrderStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "payment_status", nullable = false, length = 16)
    private PaymentStatus paymentStatus;
    @Column(name = "estimated_total_amount")
    private Long estimatedTotalAmount;
    @Column(name = "ordered_at")
    private LocalDateTime orderedAt;
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    @Column(name = "note", length = 500)
    private String note;
    @OneToMany(mappedBy = "serviceOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ServiceOrderItem> items = new ArrayList<>();
    @PrePersist void prePersist(){ if(status==null) status=ServiceOrderStatus.PENDING_PAYMENT; if(paymentStatus==null) paymentStatus=PaymentStatus.UNPAID; if(orderedAt==null) orderedAt=LocalDateTime.now(); }
}
