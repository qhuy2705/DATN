package com.PrimeCare.PrimeCare.modules.appointment.repository;

import com.PrimeCare.PrimeCare.modules.appointment.entity.ReceptionQueueCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.Optional;

public interface ReceptionQueueCounterRepository extends JpaRepository<ReceptionQueueCounter, Long> {

    Optional<ReceptionQueueCounter> findByBranchIdAndQueueDate(Long branchId, LocalDate queueDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReceptionQueueCounter> findWithLockByBranchIdAndQueueDate(Long branchId, LocalDate queueDate);
}