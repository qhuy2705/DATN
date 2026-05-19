package com.PrimeCare.PrimeCare.modules.booking_restriction.repository;

import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.PatientViolationEvent;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PatientViolationEventRepository extends JpaRepository<PatientViolationEvent, Long> {

    @Query("""
            select coalesce(sum(e.points), 0)
            from PatientViolationEvent e
            where e.identityKeyHash = :identityKeyHash
              and e.periodMonth = :periodMonth
              and e.status = com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventStatus.ACTIVE
            """)
    Integer sumActivePointsByIdentityKeyHashAndPeriodMonth(
            @Param("identityKeyHash") String identityKeyHash,
            @Param("periodMonth") String periodMonth
    );

    boolean existsByAppointment_IdAndTypeAndStatus(
            Long appointmentId,
            ViolationEventType type,
            ViolationEventStatus status
    );

    Optional<PatientViolationEvent> findFirstByAppointment_IdAndTypeAndStatusOrderByCreatedAtDesc(
            Long appointmentId,
            ViolationEventType type,
            ViolationEventStatus status
    );

    List<PatientViolationEvent> findByIdentityKeyHashAndPeriodMonthOrderByCreatedAtDesc(
            String identityKeyHash,
            String periodMonth
    );

    Optional<PatientViolationEvent> findFirstByIdentityKeyHashAndPeriodMonthAndStatusOrderByCreatedAtDesc(
            String identityKeyHash,
            String periodMonth,
            ViolationEventStatus status
    );
}
