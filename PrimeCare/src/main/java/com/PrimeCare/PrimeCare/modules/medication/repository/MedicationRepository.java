package com.PrimeCare.PrimeCare.modules.medication.repository;

import com.PrimeCare.PrimeCare.modules.medication.entity.Medication;
import com.PrimeCare.PrimeCare.shared.enums.MedicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    boolean existsByCode(String code);

    Optional<Medication> findByCode(String code);

    @Query("""
        select m
        from Medication m
        where (:status is null or m.status = :status)
          and (
                :q is null
                or lower(m.code) like lower(concat('%', :q, '%'))
                or lower(m.name) like lower(concat('%', :q, '%'))
                or lower(coalesce(m.genericName, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(m.dosageForm, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(m.unit, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(m.manufacturer, '')) like lower(concat('%', :q, '%'))
          )
        """)
    Page<Medication> searchAdmin(@Param("q") String q,
                                 @Param("status") MedicationStatus status,
                                 Pageable pageable);

    List<Medication> findByStatus(MedicationStatus status);
}
