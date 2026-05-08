package com.PrimeCare.PrimeCare.modules.service_order.repository;

import com.PrimeCare.PrimeCare.modules.billing.dto.query.CashierServiceOrderSummaryRow;
import com.PrimeCare.PrimeCare.modules.encounter.dto.query.EncounterServiceOrderSummary;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long> {

    @EntityGraph(attributePaths = {"encounter", "encounter.doctor", "encounter.patient", "branch", "items"})
    Page<ServiceOrder> findByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);

    Page<ServiceOrder> findByEncounter_Doctor_Id(Long doctorId, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.medicalService", "orderedByDoctor"})
    List<ServiceOrder> findByEncounter_IdOrderByCreatedAtDesc(Long encounterId);

    @Query("""
            select
              so.encounter.id as encounterId,
              count(distinct so.id) as serviceOrderCount,
              count(distinct case
                    when so.status = com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus.PENDING_PAYMENT
                    then so.id
                    else null
              end) as pendingPaymentOrderCount,
              count(distinct case
                    when so.status <> com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus.CANCELLED
                     and so.status <> com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus.COMPLETED
                    then so.id
                    else null
              end) as activeServiceOrderCount,
              coalesce(sum(case
                    when so.status <> com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus.CANCELLED
                     and i.status in (
                        com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus.WAITING_EXECUTION,
                        com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus.IN_PROGRESS
                     )
                    then 1
                    else 0
              end), 0) as waitingResultItemCount,
              coalesce(sum(case
                    when so.status <> com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus.CANCELLED
                     and i.status = com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus.DONE
                    then 1
                    else 0
              end), 0) as completedResultItemCount
            from ServiceOrder so
            left join so.items i
            where so.encounter.id in :encounterIds
            group by so.encounter.id
            """)
    List<EncounterServiceOrderSummary> summarizeWorkflowByEncounterIds(@Param("encounterIds") Collection<Long> encounterIds);

    boolean existsByEncounter_IdAndStatusIn(Long encounterId, Collection<ServiceOrderStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @EntityGraph(attributePaths = {
            "encounter",
            "encounter.appointment",
            "encounter.doctor",
            "encounter.patient",
            "branch",
            "orderedByDoctor",
            "items",
            "items.medicalService"
    })
    Optional<ServiceOrder> findWithLockById(Long id);

    @EntityGraph(attributePaths = {"encounter", "encounter.doctor", "branch", "items", "items.medicalService"})
    @Query("""
        select so
        from ServiceOrder so
        where (:paymentStatus is null or so.paymentStatus = :paymentStatus)
          and (:fromTime is null or so.createdAt >= :fromTime)
          and (:toTime is null or so.createdAt < :toTime)
          and (:invoiced is null
                or (:invoiced = true and exists (
                    select 1 from Invoice i where i.serviceOrder.id = so.id
                ))
                or (:invoiced = false and not exists (
                    select 1 from Invoice i where i.serviceOrder.id = so.id
                ))
              )
          and (
                :q is null or :q = ''
                or so.code = :q
                or so.code like concat(:q, '%')
                or coalesce(so.encounter.patientPhoneSnapshot, '') = :q
                or coalesce(so.encounter.patientPhoneSnapshot, '') like concat(:q, '%')
                or lower(so.encounter.patientFullNameSnapshot) like concat('%', :qLower, '%')
              )
        """)
    Page<ServiceOrder> searchCashierOrders(
            @Param("q") String q,
            @Param("qLower") String qLower,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("invoiced") Boolean invoiced,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            Pageable pageable
    );

    @Query("""
            select
              count(so) as serviceOrderCount,
              coalesce(sum(case when not exists (
                    select 1 from Invoice i where i.serviceOrder.id = so.id
              ) then 1 else 0 end), 0) as uninvoicedServiceOrderCount,
              coalesce(sum(case when so.paymentStatus in (
                    com.PrimeCare.PrimeCare.shared.enums.PaymentStatus.UNPAID,
                    com.PrimeCare.PrimeCare.shared.enums.PaymentStatus.PENDING_CONFIRMATION,
                    com.PrimeCare.PrimeCare.shared.enums.PaymentStatus.PAYMENT_REVIEW
              ) then 1 else 0 end), 0) as unpaidServiceOrderCount
            from ServiceOrder so
            where (:fromTime is null or so.createdAt >= :fromTime)
              and (:toTime is null or so.createdAt < :toTime)
            """)
    CashierServiceOrderSummaryRow summarizeCashierServiceOrders(
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );
}
