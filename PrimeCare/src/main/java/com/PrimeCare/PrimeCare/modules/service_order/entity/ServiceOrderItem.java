package com.PrimeCare.PrimeCare.modules.service_order.entity;

import com.PrimeCare.PrimeCare.modules.medical_service.entity.MedicalService;
import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "service_order_items")
public class ServiceOrderItem extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "service_order_id", nullable = false)
    private ServiceOrder serviceOrder;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "medical_service_id", nullable = false)
    private MedicalService medicalService;
    @Column(name = "service_code_snapshot", nullable = false, length = 64)
    private String serviceCodeSnapshot;
    @Column(name = "service_name_vn_snapshot", nullable = false, length = 255)
    private String serviceNameVnSnapshot;
    @Column(name = "service_name_en_snapshot", length = 255)
    private String serviceNameEnSnapshot;
    @Column(name = "price_snapshot", nullable = false)
    private Long priceSnapshot;
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    @Column(name = "line_total_amount", nullable = false)
    private Long lineTotalAmount;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32)
    private ServiceOrderItemStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "result_status", nullable = false, length = 16)
    private ServiceResultStatus resultStatus;
    @Column(name = "assigned_department_code", length = 64)
    private String assignedDepartmentCode;
    @Column(name = "queue_no")
    private Integer queueNo;
    @Column(name = "queued_at")
    private LocalDateTime queuedAt;
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    @Column(name = "refund_reason", length = 500)
    private String refundReason;
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "refunded_by_user_id")
    private com.PrimeCare.PrimeCare.modules.auth.entity.User refundedByUser;
    @Column(name = "note", length = 500)
    private String note;
    @PrePersist void prePersist(){ if(status==null) status=ServiceOrderItemStatus.PENDING_PAYMENT; if(resultStatus==null) resultStatus=ServiceResultStatus.DRAFT; if(quantity==null) quantity=1; }
}
