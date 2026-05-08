package com.PrimeCare.PrimeCare.modules.billing.repository;

import com.PrimeCare.PrimeCare.modules.billing.dto.query.CashierInvoiceSummaryRow;
import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
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
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // Eager-load service order chain for detail views
    @Override
    @NonNull
    @EntityGraph(attributePaths = {"serviceOrder", "serviceOrder.encounter", "serviceOrder.encounter.doctor", "serviceOrder.branch"})
    Optional<Invoice> findById(@NonNull Long id);

    Optional<Invoice> findByCode(String code);

    Optional<Invoice> findByIdAndServiceOrder_Encounter_Patient_Id(Long id, Long patientId);

    Page<Invoice> findByServiceOrder_Encounter_Patient_IdOrderByCreatedAtDesc(Long patientId, Pageable pageable);

    long countByServiceOrder_Encounter_Patient_Id(Long patientId);

    @EntityGraph(attributePaths = {"serviceOrder", "serviceOrder.encounter", "serviceOrder.encounter.doctor", "serviceOrder.branch"})
    Page<Invoice> findByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);

    boolean existsByServiceOrder_Id(Long serviceOrderId);

    Optional<Invoice> findByServiceOrder_Id(Long serviceOrderId);

    @EntityGraph(attributePaths = {"serviceOrder"})
    List<Invoice> findByServiceOrder_IdIn(Collection<Long> serviceOrderIds);

    Optional<Invoice> findByVnpTxnRef(String vnpTxnRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @EntityGraph(attributePaths = {
            "serviceOrder",
            "serviceOrder.encounter",
            "serviceOrder.encounter.appointment",
            "serviceOrder.encounter.doctor",
            "serviceOrder.encounter.patient",
            "serviceOrder.branch",
            "items"
    })
    Optional<Invoice> findWithLockById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    Optional<Invoice> findWithLockByVnpTxnRef(String vnpTxnRef);

    @Query("""
        select i
        from Invoice i
        where lower(i.code) like lower(concat('%', :q, '%'))
           or lower(i.serviceOrder.code) like lower(concat('%', :q, '%'))
           or lower(i.serviceOrder.encounter.patientFullNameSnapshot) like lower(concat('%', :q, '%'))
        """)
    Page<Invoice> search(@Param("q") String q, Pageable pageable);

    @Query("""
        select i
        from Invoice i
        where i.paymentStatus = :paymentStatus
          and (
                lower(i.code) like lower(concat('%', :q, '%'))
             or lower(i.serviceOrder.code) like lower(concat('%', :q, '%'))
             or lower(i.serviceOrder.encounter.patientFullNameSnapshot) like lower(concat('%', :q, '%'))
          )
        """)
    Page<Invoice> searchByPaymentStatus(
            @Param("q") String q,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            Pageable pageable
    );

    @Query(
            value = """
                    select i.id
                    from Invoice i
                    where (:paymentStatus is null or i.paymentStatus = :paymentStatus)
                      and (:fromTime is null or i.createdAt >= :fromTime)
                      and (:toTime is null or i.createdAt < :toTime)
                      and (
                            :q is null
                            or i.code = :q
                            or i.code like concat(:q, '%')
                            or i.serviceOrder.code = :q
                            or i.serviceOrder.code like concat(:q, '%')
                            or coalesce(i.serviceOrder.encounter.patientPhoneSnapshot, '') = :q
                            or coalesce(i.serviceOrder.encounter.patientPhoneSnapshot, '') like concat(:q, '%')
                            or lower(i.serviceOrder.encounter.patientFullNameSnapshot) like concat('%', :qLower, '%')
                      )
                    """,
            countQuery = """
                    select count(i.id)
                    from Invoice i
                    where (:paymentStatus is null or i.paymentStatus = :paymentStatus)
                      and (:fromTime is null or i.createdAt >= :fromTime)
                      and (:toTime is null or i.createdAt < :toTime)
                      and (
                            :q is null
                            or i.code = :q
                            or i.code like concat(:q, '%')
                            or i.serviceOrder.code = :q
                            or i.serviceOrder.code like concat(:q, '%')
                            or coalesce(i.serviceOrder.encounter.patientPhoneSnapshot, '') = :q
                            or coalesce(i.serviceOrder.encounter.patientPhoneSnapshot, '') like concat(:q, '%')
                            or lower(i.serviceOrder.encounter.patientFullNameSnapshot) like concat('%', :qLower, '%')
                      )
                    """
    )
    Page<Long> findIdsForCashier(
            @Param("q") String q,
            @Param("qLower") String qLower,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "serviceOrder",
            "serviceOrder.encounter",
            "serviceOrder.encounter.doctor",
            "serviceOrder.encounter.patient",
            "serviceOrder.branch",
            "items"
    })
    @Query("select distinct i from Invoice i where i.id in :ids")
    List<Invoice> findAllWithListDetailsByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            select
              count(i) as invoiceCount,
              coalesce(sum(case when i.paymentStatus = com.PrimeCare.PrimeCare.shared.enums.PaymentStatus.UNPAID then 1 else 0 end), 0) as unpaidInvoiceCount,
              coalesce(sum(case when i.paymentStatus = com.PrimeCare.PrimeCare.shared.enums.PaymentStatus.PENDING_CONFIRMATION then 1 else 0 end), 0) as pendingConfirmationInvoiceCount,
              coalesce(sum(case when i.paymentStatus = com.PrimeCare.PrimeCare.shared.enums.PaymentStatus.PAYMENT_REVIEW then 1 else 0 end), 0) as paymentReviewInvoiceCount,
              coalesce(sum(case when i.paymentStatus = com.PrimeCare.PrimeCare.shared.enums.PaymentStatus.PAID then 1 else 0 end), 0) as paidInvoiceCount,
              coalesce(sum(case when i.paymentStatus = com.PrimeCare.PrimeCare.shared.enums.PaymentStatus.REFUNDED then 1 else 0 end), 0) as refundedInvoiceCount,
              coalesce(sum(case when i.paymentStatus = com.PrimeCare.PrimeCare.shared.enums.PaymentStatus.PAID then i.totalAmount else 0 end), 0) as paidRevenue
            from Invoice i
            where (:fromTime is null or i.createdAt >= :fromTime)
              and (:toTime is null or i.createdAt < :toTime)
            """)
    CashierInvoiceSummaryRow summarizeCashierInvoices(
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );

    @Query("""
        select coalesce(sum(i.totalAmount), 0)
        from Invoice i
        where i.paymentStatus = :status
          and i.paidAt between :from and :to
        """)
    Long sumTotalAmountByPaymentStatusAndPaidAtBetween(
            @Param("status") PaymentStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
