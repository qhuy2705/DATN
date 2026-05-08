package com.PrimeCare.PrimeCare.modules.prescription.repository;

import com.PrimeCare.PrimeCare.modules.prescription.entity.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {
}