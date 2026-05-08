package com.PrimeCare.PrimeCare.modules.encounter.repository;

import com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardAggregateRow;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
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

public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    // Eager-load key relations to prevent N+1 on detail views
    @Override
    @NonNull
    @EntityGraph(attributePaths = {
            "branch",
            "specialty",
            "doctor",
            "patient",
            "appointment",
            "appointment.branch",
            "appointment.specialty",
            "appointment.doctor",
            "appointment.confirmedBy",
            "appointment.intakeCompletedBy",
            "appointment.arrivedBy",
            "appointment.checkedInBy"
    })
    Optional<Encounter> findById(@NonNull Long id);

    @EntityGraph(attributePaths = {
            "branch",
            "specialty",
            "doctor",
            "patient",
            "appointment",
            "appointment.branch",
            "appointment.specialty",
            "appointment.doctor",
            "appointment.confirmedBy",
            "appointment.intakeCompletedBy",
            "appointment.arrivedBy",
            "appointment.checkedInBy"
    })
    Optional<Encounter> findByAppointment_Id(Long appointmentId);

    @EntityGraph(attributePaths = {"appointment"})
    List<Encounter> findByAppointment_IdIn(Collection<Long> appointmentIds);

    Optional<Encounter> findByCode(String code);

    @EntityGraph(attributePaths = {
            "branch",
            "specialty",
            "doctor",
            "patient",
            "appointment",
            "appointment.branch",
            "appointment.specialty",
            "appointment.doctor",
            "appointment.confirmedBy",
            "appointment.intakeCompletedBy",
            "appointment.arrivedBy",
            "appointment.checkedInBy"
    })
    Optional<Encounter> findByIdAndDoctor_Id(Long id, Long doctorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @EntityGraph(attributePaths = {
            "branch",
            "specialty",
            "doctor",
            "patient",
            "appointment",
            "appointment.branch",
            "appointment.doctor",
            "appointment.confirmedBy",
            "appointment.arrivedBy",
            "appointment.checkedInBy"
    })
    @Query("select e from Encounter e where e.id = :id")
    Optional<Encounter> findWithLockById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"branch", "specialty", "doctor", "patient"})
    Page<Encounter> findByDoctor_IdAndStatusIn(Long doctorId, java.util.Collection<EncounterStatus> statuses, Pageable pageable);

    boolean existsByDoctor_IdAndPatient_IdAndStatusIn(
            Long doctorId,
            Long patientId,
            java.util.Collection<EncounterStatus> statuses
    );

    long countByStartedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByStatus(EncounterStatus status);
    @Query("""
        select new com.PrimeCare.PrimeCare.modules.dashboard.dto.query.DashboardAggregateRow(
            e.doctor.id,
            cast(e.doctor.id as string),
            e.doctor.fullName,
            count(e.id)
        )
        from Encounter e
        where e.startedAt between :from and :to
        group by e.doctor.id, e.doctor.fullName
        order by count(e.id) desc
        """)
    java.util.List<DashboardAggregateRow> countEncountersByDoctor(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );
}
