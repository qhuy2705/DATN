package com.PrimeCare.PrimeCare.modules.doctor_schedule.repository;

import com.PrimeCare.PrimeCare.modules.doctor_schedule.entity.DoctorWorkSchedule;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DoctorWorkScheduleRepository extends JpaRepository<DoctorWorkSchedule, Long> {

    Optional<DoctorWorkSchedule> findByDoctor_IdAndWorkDateAndSession(
            Long doctorId,
            LocalDate workDate,
            BranchSessionType session
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DoctorWorkSchedule> findWithLockByDoctor_IdAndWorkDateAndSession(
            Long doctorId,
            LocalDate workDate,
            BranchSessionType session
    );

    @EntityGraph(attributePaths = {"doctor", "doctor.branch"})
    Page<DoctorWorkSchedule> findByDoctor_IdAndWorkDateBetween(
            Long doctorId,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"doctor", "doctor.branch"})
    List<DoctorWorkSchedule> findByDoctor_IdAndWorkDateBetweenOrderByWorkDateAscSessionAsc(
            Long doctorId,
            LocalDate from,
            LocalDate to
    );

    void deleteByDoctor_IdAndWorkDateAndSession(
            Long doctorId,
            LocalDate workDate,
            BranchSessionType session
    );

    void deleteByDoctor_IdAndWorkDateBetween(
            Long doctorId,
            LocalDate from,
            LocalDate to
    );
}
