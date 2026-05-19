package com.PrimeCare.PrimeCare.modules.service_order.repository;

import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
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

import java.util.Collection;
import java.util.Optional;

public interface ServiceOrderItemRepository extends JpaRepository<ServiceOrderItem, Long> {

    @EntityGraph(attributePaths = {
            "medicalService",
            "serviceOrder",
            "serviceOrder.encounter",
            "serviceOrder.encounter.appointment",
            "serviceOrder.encounter.doctor",
            "serviceOrder.encounter.branch"
    })
    @Query("""
            select i
            from ServiceOrderItem i
            join i.serviceOrder o
            join o.encounter e
            where i.status in :statuses
              and (:departmentCode is null or i.assignedDepartmentCode = :departmentCode)
              and (:resultStatus is null or i.resultStatus = :resultStatus)
              and (
                    :q is null
                    or o.code = :q
                    or o.code like concat(:q, '%')
                    or coalesce(i.serviceCodeSnapshot, '') = :q
                    or coalesce(i.serviceCodeSnapshot, '') like concat(:q, '%')
                    or coalesce(e.patientPhoneSnapshot, '') = :q
                    or coalesce(e.patientPhoneSnapshot, '') like concat(:q, '%')
                    or lower(e.patientFullNameSnapshot) like concat('%', :qLower, '%')
                    or lower(e.doctor.fullName) like concat('%', :qLower, '%')
                    or lower(i.serviceNameVnSnapshot) like concat('%', :qLower, '%')
                  )
            order by case when i.queueNo is null then 1 else 0 end asc,
                     i.queueNo asc,
                     case when i.queuedAt is null then 1 else 0 end asc,
                     i.queuedAt asc,
                     i.createdAt asc
            """)
    Page<ServiceOrderItem> searchServiceDeskQueue(
            @Param("departmentCode") String departmentCode,
            @Param("statuses") Collection<ServiceOrderItemStatus> statuses,
            @Param("resultStatus") ServiceResultStatus resultStatus,
            @Param("q") String q,
            @Param("qLower") String qLower,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @EntityGraph(attributePaths = {
            "medicalService",
            "serviceOrder",
            "serviceOrder.items",
            "serviceOrder.encounter",
            "serviceOrder.encounter.appointment",
            "serviceOrder.encounter.doctor",
            "serviceOrder.encounter.branch"
    })
    @Query("select i from ServiceOrderItem i where i.id = :id")
    Optional<ServiceOrderItem> findWithLockById(@Param("id") Long id);
}
