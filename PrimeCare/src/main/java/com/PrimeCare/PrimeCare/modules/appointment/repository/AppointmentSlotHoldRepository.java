package com.PrimeCare.PrimeCare.modules.appointment.repository;

import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentSlotHold;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppointmentSlotHoldRepository extends JpaRepository<AppointmentSlotHold, Long> {

    @EntityGraph(attributePaths = {"originalAppointment", "originalAppointment.branch", "originalAppointment.specialty",
            "originalAppointment.doctor", "originalAppointment.patient", "doctor", "branch", "specialty", "patient"})
    Optional<AppointmentSlotHold> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"originalAppointment", "originalAppointment.branch", "originalAppointment.specialty",
            "originalAppointment.doctor", "originalAppointment.patient", "doctor", "branch", "specialty", "patient"})
    @Query("select h from AppointmentSlotHold h where h.tokenHash = :tokenHash")
    Optional<AppointmentSlotHold> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @EntityGraph(attributePaths = {"originalAppointment", "doctor", "branch", "specialty", "patient"})
    @Query("select h from AppointmentSlotHold h where h.id = :id")
    Optional<AppointmentSlotHold> findDetailById(@Param("id") Long id);

    List<AppointmentSlotHold> findByOriginalAppointment_IdAndStatusIn(
            Long originalAppointmentId,
            Collection<AppointmentSlotHoldStatus> statuses
    );

    @EntityGraph(attributePaths = {"originalAppointment", "doctor"})
    @Query("""
            select h
            from AppointmentSlotHold h
            where h.originalAppointment.id in :appointmentIds
            order by h.originalAppointment.id asc, h.createdAt desc, h.id desc
            """)
    List<AppointmentSlotHold> findLatestByOriginalAppointmentIds(
            @Param("appointmentIds") Collection<Long> appointmentIds
    );

    @Query("""
            select h
            from AppointmentSlotHold h
            where h.doctor.id = :doctorId
              and h.visitDate = :visitDate
              and h.session = :session
              and h.status in :statuses
              and h.expiresAt > :now
              and h.slotStart < :slotEnd
              and h.slotEnd > :slotStart
            """)
    List<AppointmentSlotHold> findActiveOverlapping(
            @Param("doctorId") Long doctorId,
            @Param("visitDate") LocalDate visitDate,
            @Param("session") BranchSessionType session,
            @Param("slotStart") LocalTime slotStart,
            @Param("slotEnd") LocalTime slotEnd,
            @Param("statuses") Collection<AppointmentSlotHoldStatus> statuses,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select h
            from AppointmentSlotHold h
            where h.doctor.id = :doctorId
              and h.visitDate between :from and :to
              and h.status in :statuses
              and h.expiresAt > :now
            """)
    List<AppointmentSlotHold> findActiveByDoctorAndDateRange(
            @Param("doctorId") Long doctorId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") Collection<AppointmentSlotHoldStatus> statuses,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select h
            from AppointmentSlotHold h
            where h.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus.HELD
              and h.reminderQueuedAt is null
              and h.expiresAt > :now
              and h.expiresAt <= :cutoff
            order by h.expiresAt asc
            """)
    List<AppointmentSlotHold> findDueForReminder(
            @Param("now") LocalDateTime now,
            @Param("cutoff") LocalDateTime cutoff
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"originalAppointment", "originalAppointment.branch", "originalAppointment.specialty",
            "originalAppointment.doctor", "originalAppointment.patient", "doctor", "branch", "specialty", "patient"})
    @Query("""
            select h
            from AppointmentSlotHold h
            where h.status in :statuses
              and h.expiresAt <= :now
            order by h.expiresAt asc
            """)
    List<AppointmentSlotHold> findDueForExpiryForUpdate(
            @Param("statuses") Collection<AppointmentSlotHoldStatus> statuses,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Transactional
    @Query("""
            update AppointmentSlotHold h
            set h.reminderQueuedAt = :queuedAt
            where h.id = :holdId
              and h.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus.HELD
              and h.reminderQueuedAt is null
              and h.expiresAt > :queuedAt
            """)
    int markReminderQueued(
            @Param("holdId") Long holdId,
            @Param("queuedAt") LocalDateTime queuedAt
    );

    @Modifying
    @Transactional
    @Query("""
            update AppointmentSlotHold h
            set h.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus.EXPIRED,
                h.expiredAt = :expiredAt
            where h.id = :holdId
              and h.status = com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus.HELD
              and h.expiresAt <= :expiredAt
            """)
    int expireHeldHold(
            @Param("holdId") Long holdId,
            @Param("expiredAt") LocalDateTime expiredAt
    );
}
