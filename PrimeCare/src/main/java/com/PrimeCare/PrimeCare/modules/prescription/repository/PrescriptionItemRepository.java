package com.PrimeCare.PrimeCare.modules.prescription.repository;

import com.PrimeCare.PrimeCare.modules.prescription.entity.PrescriptionItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PrescriptionItem i where i.id = :id")
    Optional<PrescriptionItem> findWithLockById(@Param("id") Long id);
}
