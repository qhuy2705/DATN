package com.PrimeCare.PrimeCare.modules.service_result.repository;

import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceResultRepository extends JpaRepository<ServiceResult, Long> {
    Optional<ServiceResult> findByServiceOrderItem_Id(Long serviceOrderItemId);

    Optional<ServiceResult> findByIdAndServiceOrderItem_ServiceOrder_Encounter_Patient_Id(Long id, Long patientId);

    @EntityGraph(attributePaths = {
            "serviceOrderItem",
            "serviceOrderItem.serviceOrder",
            "performedByUser",
            "verifiedByUser"
    })
    List<ServiceResult> findByServiceOrderItem_ServiceOrder_Encounter_Id(Long encounterId);

    @EntityGraph(attributePaths = {
            "serviceOrderItem",
            "serviceOrderItem.serviceOrder",
            "serviceOrderItem.serviceOrder.encounter"
    })
    Page<ServiceResult> findByServiceOrderItem_ServiceOrder_Encounter_Patient_IdOrderByVerifiedAtDescCreatedAtDesc(Long patientId, Pageable pageable);

    long countByServiceOrderItem_ServiceOrder_Encounter_Patient_Id(Long patientId);

    @org.springframework.data.jpa.repository.Query("""
            select count(sr)
            from ServiceResult sr
            where sr.serviceOrderItem.serviceOrder.encounter.patient.id = :patientId
              and sr.reportPdfPath is not null
              and trim(sr.reportPdfPath) <> ''
            """)
    long countReadyByPatientId(@org.springframework.data.repository.query.Param("patientId") Long patientId);

    List<ServiceResult> findByServiceOrderItem_IdIn(Collection<Long> serviceOrderItemIds);
}
