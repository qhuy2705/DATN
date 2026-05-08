package com.PrimeCare.PrimeCare.modules.appointment.repository;

import com.PrimeCare.PrimeCare.modules.appointment.dto.query.AppointmentStatusCountRow;
import com.PrimeCare.PrimeCare.modules.appointment.dto.query.ReceptionQueueSummaryRow;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardAggregateRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardDoctorKpiRow;
import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardSpecialtyKpiRow;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Eager-load key relations to prevent N+1 on detail views
    @Override
    @NonNull
    @EntityGraph(attributePaths = {"branch", "specialty", "doctor", "patient",
            "confirmedBy", "processingBy", "intakeCompletedBy", "arrivedBy", "checkedInBy", "noShowMarkedBy",
            "rescheduledFromAppointment"})
    Optional<Appointment> findById(@NonNull Long id);

    Optional<Appointment> findByIdAndStatus(Long id, AppointmentStatus status);

    Optional<Appointment> findByIdAndDoctor_Id(Long id, Long doctorId);

    List<Appointment> findByRescheduledFromAppointment_Id(Long appointmentId);

    Optional<Appointment> findFirstByRescheduledFromAppointment_IdOrderByCreatedAtDesc(Long appointmentId);

    List<Appointment> findByDoctor_IdAndVisitDateBetweenAndStatusIn(
            Long doctorId,
            LocalDate startDate,
            LocalDate endDate,
            List<AppointmentStatus> statuses
    );

    List<Appointment> findByDoctor_IdAndVisitDateAndSessionAndStatusInOrderByEtaStartAsc(
            Long doctorId,
            LocalDate visitDate,
            BranchSessionType session,
            Collection<AppointmentStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a
            from Appointment a
            where a.doctor.id = :doctorId
              and a.visitDate = :visitDate
              and a.session = :session
              and a.status in :statuses
            """)
    List<Appointment> findWithLockByDoctor_IdAndVisitDateAndSessionAndStatusIn(
            @Param("doctorId") Long doctorId,
            @Param("visitDate") LocalDate visitDate,
            @Param("session") BranchSessionType session,
            @Param("statuses") Collection<AppointmentStatus> statuses
    );

    long countByDoctor_IdAndVisitDateAndSessionAndEtaStartAndStatusIn(
            Long doctorId,
            LocalDate visitDate,
            BranchSessionType session,
            LocalTime etaStart,
            Collection<AppointmentStatus> statuses
    );

    Optional<Appointment> findByCode(String code);

    Optional<Appointment> findByIdAndPatient_Id(Long id, Long patientId);

    Page<Appointment> findByPatient_IdOrderByVisitDateDescCreatedAtDesc(Long patientId, Pageable pageable);

    long countByPatient_Id(Long patientId);

    long countByPatient_IdAndVisitDateGreaterThanEqualAndStatusIn(Long patientId, LocalDate visitDate, Collection<AppointmentStatus> statuses);

    Optional<Appointment> findFirstByPatient_IdAndVisitDateGreaterThanEqualAndStatusInOrderByVisitDateAscEtaStartAscCreatedAtAsc(Long patientId, LocalDate visitDate, Collection<AppointmentStatus> statuses);

    boolean existsByDoctor_IdAndVisitDateAndSessionAndEtaStartAndPatientPhoneAndStatusIn(
            Long doctorId,
            LocalDate visitDate,
            BranchSessionType session,
            LocalTime etaStart,
            String patientPhone,
            Collection<AppointmentStatus> statuses
    );

    boolean existsByDoctor_IdAndPatient_IdAndStatusIn(
            Long doctorId,
            Long patientId,
            Collection<AppointmentStatus> statuses
    );

    List<Appointment> findByStatusAndCreatedAtBefore(AppointmentStatus status, LocalDateTime before);
    @Query("""
            select a
            from Appointment a
            where a.visitDate between :from and :to
              and a.status in :statuses
              and a.patientPhone is not null
              and trim(a.patientPhone) <> ''
            order by a.visitDate asc, a.etaStart asc
            """)
    List<Appointment> findForReminder(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") Collection<AppointmentStatus> statuses
    );

    @EntityGraph(attributePaths = {"branch", "specialty", "doctor", "patient", "processingBy"})
    @Query("""
            select a
            from Appointment a
            where (:status is null or a.status = :status)
              and (:visitDate is null or a.visitDate = :visitDate)
              and (:branchId is null or a.branch.id = :branchId)
              and (:doctorId is null or a.doctor.id = :doctorId)
              and (:specialtyId is null or a.specialty.id = :specialtyId)
              and (:followUpPending is null or a.followUpPending = :followUpPending)
              and (
                    :overdueOnly = false
                    or (
                        a.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus.CONFIRMED
                        and a.visitDate = :overdueVisitDate
                        and a.etaStart is not null
                        and a.etaStart < :overdueCutoffTime
                        and (a.arrivalStatus is null or a.arrivalStatus <> com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus.ARRIVED)
                        and a.checkedInAt is null
                    )
              )
              and (
                    :q is null
                    or a.code = :q
                    or a.code like concat(:q, '%')
                    or a.patientPhone = :q
                    or a.patientPhone like concat(:q, '%')
                    or lower(a.patientFullName) like concat('%', :qLower, '%')
                    or lower(a.doctor.fullName) like concat('%', :qLower, '%')
              )
            order by a.visitDate asc, a.etaStart asc, a.createdAt asc
            """)
    Page<Appointment> search(
            @Param("status") AppointmentStatus status,
            @Param("visitDate") LocalDate visitDate,
            @Param("branchId") Long branchId,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId,
            @Param("overdueOnly") boolean overdueOnly,
            @Param("followUpPending") Boolean followUpPending,
            @Param("overdueVisitDate") LocalDate overdueVisitDate,
            @Param("overdueCutoffTime") LocalTime overdueCutoffTime,
            @Param("q") String q,
            @Param("qLower") String qLower,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"branch", "specialty", "doctor", "patient"})
    @Query("""
            select a
            from Appointment a
            where a.doctor.id = :doctorId
              and a.visitDate between :from and :to
              and a.status in :statuses
            order by a.visitDate asc, a.etaStart asc
            """)
    Page<Appointment> findDoctorCalendar(
            @Param("doctorId") Long doctorId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") Collection<AppointmentStatus> statuses,
            Pageable pageable
    );

    @Modifying
    @Transactional
    @Query("""
            update Appointment a
            set a.processingBy = :staffUser,
                a.processingStartedAt = :now,
                a.processingExpiresAt = :expireAt
            where a.id = :appointmentId
              and (
                    a.status in (
                        com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus.REQUESTED,
                        com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus.CONFIRMED
                    )
                    or (
                        a.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus.NO_SHOW
                        and a.followUpPending = true
                    )
                  )
              and (
                    a.processingBy is null
                    or a.processingExpiresAt is null
                    or a.processingExpiresAt < :now
                  )
            """)
    int claimForProcessing(
            @Param("appointmentId") Long appointmentId,
            @Param("staffUser") User staffUser,
            @Param("now") LocalDateTime now,
            @Param("expireAt") LocalDateTime expireAt
    );

    @Modifying
    @Transactional
    @Query("""
            update Appointment a
            set a.processingBy = null,
                a.processingStartedAt = null,
                a.processingExpiresAt = null
            where a.id = :appointmentId
              and a.processingBy.id = :staffId
            """)
    int releaseClaim(
            @Param("appointmentId") Long appointmentId,
            @Param("staffId") Long staffId
    );


    @Modifying
    @Transactional
    @Query("""
            update Appointment a
            set a.processingExpiresAt = :expireAt
            where a.id = :appointmentId
              and a.processingBy.id = :staffId
              and a.processingExpiresAt is not null
              and a.processingExpiresAt >= :now
            """)
    int heartbeatClaim(
            @Param("appointmentId") Long appointmentId,
            @Param("staffId") Long staffId,
            @Param("now") LocalDateTime now,
            @Param("expireAt") LocalDateTime expireAt
    );

    @EntityGraph(attributePaths = {"branch", "specialty", "doctor", "patient", "arrivedBy", "checkedInBy"})
    @Query("""
            select a
            from Appointment a
            where (:visitDate is null or a.visitDate = :visitDate)
              and (:branchId is null or a.branch.id = :branchId)
              and (:doctorId is null or a.doctor.id = :doctorId)
              and (:specialtyId is null or a.specialty.id = :specialtyId)
              and (:arrivalStatus is null or a.arrivalStatus = :arrivalStatus)
              and (:sourceType is null or a.sourceType = :sourceType)
              and a.status in :statuses
              and (
                    :overdueOnly = false
                    or (
                        a.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus.CONFIRMED
                        and a.visitDate = :overdueVisitDate
                        and a.etaStart is not null
                        and a.etaStart < :overdueCutoffTime
                        and (a.arrivalStatus is null or a.arrivalStatus <> com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus.ARRIVED)
                        and a.checkedInAt is null
                    )
              )
              and (
                    :q is null
                    or a.code = :q
                    or a.code like concat(:q, '%')
                    or a.patientPhone = :q
                    or a.patientPhone like concat(:q, '%')
                    or lower(a.patientFullName) like concat('%', :qLower, '%')
                    or lower(a.doctor.fullName) like concat('%', :qLower, '%')
              )
            order by
              case when a.arrivalStatus = com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus.ARRIVED then 0 else 1 end,
              a.receptionQueueNo asc,
              a.visitDate asc,
              a.etaStart asc,
              a.createdAt asc
            """)
    Page<Appointment> searchReceptionQueue(
            @Param("visitDate") LocalDate visitDate,
            @Param("branchId") Long branchId,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId,
            @Param("arrivalStatus") ArrivalStatus arrivalStatus,
            @Param("sourceType") AppointmentSourceType sourceType,
            @Param("statuses") Collection<AppointmentStatus> statuses,
            @Param("overdueOnly") boolean overdueOnly,
            @Param("overdueVisitDate") LocalDate overdueVisitDate,
            @Param("overdueCutoffTime") LocalTime overdueCutoffTime,
            @Param("q") String q,
            @Param("qLower") String qLower,
            Pageable pageable
    );

    @Query("""
            select
              count(a) as total,
              coalesce(sum(case when a.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus.REQUESTED then 1 else 0 end), 0) as requested,
              coalesce(sum(case when a.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus.CONFIRMED then 1 else 0 end), 0) as confirmed,
              coalesce(sum(case when a.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus.CHECKED_IN then 1 else 0 end), 0) as checkedIn,
              coalesce(sum(case when a.arrivalStatus = com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus.ARRIVED then 1 else 0 end), 0) as arrived,
              coalesce(sum(case when a.arrivalStatus = com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus.NOT_ARRIVED then 1 else 0 end), 0) as notArrived,
              coalesce(sum(case when a.sourceType = com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType.WALK_IN then 1 else 0 end), 0) as walkIn,
              coalesce(sum(case when upper(a.triagePriority) in ('PRIORITY', 'HIGH', 'URGENT', 'EMERGENCY') then 1 else 0 end), 0) as priority,
              coalesce(sum(case when upper(a.triagePriority) in ('URGENT', 'EMERGENCY') then 1 else 0 end), 0) as urgent
            from Appointment a
            where (:visitDate is null or a.visitDate = :visitDate)
              and (:branchId is null or a.branch.id = :branchId)
              and (:doctorId is null or a.doctor.id = :doctorId)
              and (:specialtyId is null or a.specialty.id = :specialtyId)
              and (:arrivalStatus is null or a.arrivalStatus = :arrivalStatus)
              and (:sourceType is null or a.sourceType = :sourceType)
              and a.status in :statuses
              and (
                    :overdueOnly = false
                    or (
                        a.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus.CONFIRMED
                        and a.visitDate = :overdueVisitDate
                        and a.etaStart is not null
                        and a.etaStart < :overdueCutoffTime
                        and (a.arrivalStatus is null or a.arrivalStatus <> com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus.ARRIVED)
                        and a.checkedInAt is null
                    )
              )
              and (
                    :q is null
                    or a.code = :q
                    or a.code like concat(:q, '%')
                    or a.patientPhone = :q
                    or a.patientPhone like concat(:q, '%')
                    or lower(a.patientFullName) like concat('%', :qLower, '%')
                    or lower(a.doctor.fullName) like concat('%', :qLower, '%')
              )
            """)
    ReceptionQueueSummaryRow summarizeReceptionQueue(
            @Param("visitDate") LocalDate visitDate,
            @Param("branchId") Long branchId,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId,
            @Param("arrivalStatus") ArrivalStatus arrivalStatus,
            @Param("sourceType") AppointmentSourceType sourceType,
            @Param("statuses") Collection<AppointmentStatus> statuses,
            @Param("overdueOnly") boolean overdueOnly,
            @Param("overdueVisitDate") LocalDate overdueVisitDate,
            @Param("overdueCutoffTime") LocalTime overdueCutoffTime,
            @Param("q") String q,
            @Param("qLower") String qLower
    );

    @Query("""
            select count(a)
            from Appointment a
            where (:visitDate is null or a.visitDate = :visitDate)
              and (:branchId is null or a.branch.id = :branchId)
              and (:doctorId is null or a.doctor.id = :doctorId)
              and (:specialtyId is null or a.specialty.id = :specialtyId)
              and (:arrivalStatus is null or a.arrivalStatus = :arrivalStatus)
              and (:sourceType is null or a.sourceType = :sourceType)
              and a.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus.CONFIRMED
              and a.visitDate = :overdueVisitDate
              and a.etaStart is not null
              and a.etaStart < :overdueCutoffTime
              and (a.arrivalStatus is null or a.arrivalStatus <> com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus.ARRIVED)
              and a.checkedInAt is null
              and (
                    :q is null
                    or a.code = :q
                    or a.code like concat(:q, '%')
                    or a.patientPhone = :q
                    or a.patientPhone like concat(:q, '%')
                    or lower(a.patientFullName) like concat('%', :qLower, '%')
                    or lower(a.doctor.fullName) like concat('%', :qLower, '%')
              )
            """)
    long countReceptionOverdue(
            @Param("visitDate") LocalDate visitDate,
            @Param("branchId") Long branchId,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId,
            @Param("arrivalStatus") ArrivalStatus arrivalStatus,
            @Param("sourceType") AppointmentSourceType sourceType,
            @Param("overdueVisitDate") LocalDate overdueVisitDate,
            @Param("overdueCutoffTime") LocalTime overdueCutoffTime,
            @Param("q") String q,
            @Param("qLower") String qLower
    );

    @Query("""
            select count(a)
            from Appointment a
            where (:visitDate is null or a.visitDate = :visitDate)
              and (:branchId is null or a.branch.id = :branchId)
              and (:doctorId is null or a.doctor.id = :doctorId)
              and (:specialtyId is null or a.specialty.id = :specialtyId)
              and (:arrivalStatus is null or a.arrivalStatus = :arrivalStatus)
              and (:sourceType is null or a.sourceType = :sourceType)
              and a.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus.NO_SHOW
              and a.followUpPending = true
              and (
                    :q is null
                    or a.code = :q
                    or a.code like concat(:q, '%')
                    or a.patientPhone = :q
                    or a.patientPhone like concat(:q, '%')
                    or lower(a.patientFullName) like concat('%', :qLower, '%')
                    or lower(a.doctor.fullName) like concat('%', :qLower, '%')
              )
            """)
    long countNoShowFollowUpPending(
            @Param("visitDate") LocalDate visitDate,
            @Param("branchId") Long branchId,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId,
            @Param("arrivalStatus") ArrivalStatus arrivalStatus,
            @Param("sourceType") AppointmentSourceType sourceType,
            @Param("q") String q,
            @Param("qLower") String qLower
    );

    @EntityGraph(attributePaths = {"branch", "specialty", "doctor", "patient"})
    @Query("""
            select a
            from Appointment a
            where a.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus.CONFIRMED
              and a.visitDate = :visitDate
              and a.etaStart is not null
              and a.etaStart < :cutoffTime
              and (a.arrivalStatus is null or a.arrivalStatus <> com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus.ARRIVED)
              and a.checkedInAt is null
            order by a.visitDate asc, a.etaStart asc, a.createdAt asc
            """)
    List<Appointment> findOverdueConfirmedForNotification(
            @Param("visitDate") LocalDate visitDate,
            @Param("cutoffTime") LocalTime cutoffTime
    );

    @Query("""
            select a.status as status, count(a) as count
            from Appointment a
            where (:visitDate is null or a.visitDate = :visitDate)
              and (:branchId is null or a.branch.id = :branchId)
              and (:doctorId is null or a.doctor.id = :doctorId)
              and (:specialtyId is null or a.specialty.id = :specialtyId)
              and a.status in :statuses
            group by a.status
            """)
    List<AppointmentStatusCountRow> countSummaryByStatus(
            @Param("visitDate") LocalDate visitDate,
            @Param("branchId") Long branchId,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId,
            @Param("statuses") Collection<AppointmentStatus> statuses
    );

    @Query("""
            select a.status as status, count(a) as count
            from Appointment a
            where a.doctor.id = :doctorId
              and a.visitDate between :fromDate and :toDate
              and a.status in :statuses
            group by a.status
            """)
    List<AppointmentStatusCountRow> countDoctorSummaryByStatus(
            @Param("doctorId") Long doctorId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("statuses") Collection<AppointmentStatus> statuses
    );
}
