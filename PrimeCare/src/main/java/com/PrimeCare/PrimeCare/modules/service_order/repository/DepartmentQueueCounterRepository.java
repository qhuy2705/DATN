package com.PrimeCare.PrimeCare.modules.service_order.repository;

import com.PrimeCare.PrimeCare.modules.service_order.entity.DepartmentQueueCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.Optional;

public interface DepartmentQueueCounterRepository extends JpaRepository<DepartmentQueueCounter, Long> {

    Optional<DepartmentQueueCounter> findByDepartmentCodeAndQueueDate(String departmentCode, LocalDate queueDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DepartmentQueueCounter> findWithLockByDepartmentCodeAndQueueDate(String departmentCode, LocalDate queueDate);
}