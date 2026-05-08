package com.PrimeCare.PrimeCare.modules.patient.repository;

import com.PrimeCare.PrimeCare.modules.patient.entity.PatientAllergy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, Long> {
    List<PatientAllergy> findByPatient_IdOrderByCreatedAtDesc(Long patientId);
}
