package com.PrimeCare.PrimeCare.modules.prescription.repository;

import com.PrimeCare.PrimeCare.modules.prescription.entity.PrescriptionPdfJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionPdfJobRepository extends JpaRepository<PrescriptionPdfJob, Long> {
}