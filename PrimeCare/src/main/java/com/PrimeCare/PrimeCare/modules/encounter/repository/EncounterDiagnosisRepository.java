package com.PrimeCare.PrimeCare.modules.encounter.repository;

import com.PrimeCare.PrimeCare.modules.encounter.entity.EncounterDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EncounterDiagnosisRepository extends JpaRepository<EncounterDiagnosis, Long> {

    List<EncounterDiagnosis> findByEncounter_IdOrderByDisplayOrderAsc(Long encounterId);

    List<EncounterDiagnosis> findByEncounter_IdAndDiagnosisTypeOrderByDisplayOrderAsc(
            Long encounterId,
            EncounterDiagnosis.DiagnosisType diagnosisType
    );

    void deleteByEncounter_IdAndDiagnosisType(Long encounterId, EncounterDiagnosis.DiagnosisType diagnosisType);

    long countByEncounter_IdAndDiagnosisType(Long encounterId, EncounterDiagnosis.DiagnosisType diagnosisType);

    @Query("""
            select ed from EncounterDiagnosis ed
            join fetch ed.icd10Code
            where ed.encounter.id = :encounterId
            order by ed.diagnosisType, ed.displayOrder
            """)
    List<EncounterDiagnosis> findWithIcd10ByEncounterId(@Param("encounterId") Long encounterId);
}
