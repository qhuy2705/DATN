package com.PrimeCare.PrimeCare.modules.prescription.repository;

import com.PrimeCare.PrimeCare.modules.encounter.dto.query.EncounterPrescriptionCount;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    @EntityGraph(attributePaths = {"encounter", "doctorUser", "items", "items.medication"})
    @Query("select p from Prescription p where p.id = :id")
    Optional<Prescription> findWithDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"encounter", "items", "items.medication", "doctorUser"})
    Page<Prescription> findByEncounter_IdOrderByCreatedAtDesc(Long encounterId, Pageable pageable);

    boolean existsByEncounter_IdAndStatus(Long encounterId, PrescriptionStatus status);

    long countByEncounter_IdAndStatus(Long encounterId, PrescriptionStatus status);

    @Query("""
            select p.encounter.id as encounterId, count(p.id) as count
            from Prescription p
            where p.encounter.id in :encounterIds
              and p.status = :status
            group by p.encounter.id
            """)
    List<EncounterPrescriptionCount> countByEncounterIdsAndStatus(
            @Param("encounterIds") Collection<Long> encounterIds,
            @Param("status") PrescriptionStatus status
    );

    Page<Prescription> findByStatus(PrescriptionStatus status, Pageable pageable);

    @Query(
            value = """
                    select p.id
                    from Prescription p
                    join p.encounter e
                    left join e.patient patient
                    left join e.doctor encounterDoctor
                    left join e.appointment appointment
                    left join p.doctorUser doctorUser
                    left join doctorUser.doctorProfile prescribingDoctor
                    where (:status is null or p.status = :status)
                      and (
                            :keywordLike is null
                            or coalesce(p.code, '') = :keywordExact
                            or coalesce(p.code, '') like :keywordPrefix
                            or (:keywordId is not null and p.id = :keywordId)
                            or (:keywordId is not null and e.id = :keywordId)
                            or coalesce(e.code, '') = :keywordExact
                            or coalesce(e.code, '') like :keywordPrefix
                            or coalesce(appointment.code, '') = :keywordExact
                            or coalesce(appointment.code, '') like :keywordPrefix
                            or coalesce(e.patientPhoneSnapshot, '') = :keywordExact
                            or coalesce(e.patientPhoneSnapshot, '') like :keywordPrefix
                            or coalesce(patient.phone, '') = :keywordExact
                            or coalesce(patient.phone, '') like :keywordPrefix
                            or coalesce(appointment.patientPhone, '') = :keywordExact
                            or coalesce(appointment.patientPhone, '') like :keywordPrefix
                            or lower(coalesce(e.patientFullNameSnapshot, '')) like :keywordLike
                            or lower(coalesce(patient.fullName, '')) like :keywordLike
                            or lower(coalesce(patient.code, '')) like :keywordLike
                            or lower(coalesce(encounterDoctor.fullName, '')) like :keywordLike
                            or lower(coalesce(prescribingDoctor.fullName, '')) like :keywordLike
                            or lower(coalesce(appointment.patientFullName, '')) like :keywordLike
                      )
                    """,
            countQuery = """
                    select count(p.id)
                    from Prescription p
                    join p.encounter e
                    left join e.patient patient
                    left join e.doctor encounterDoctor
                    left join e.appointment appointment
                    left join p.doctorUser doctorUser
                    left join doctorUser.doctorProfile prescribingDoctor
                    where (:status is null or p.status = :status)
                      and (
                            :keywordLike is null
                            or coalesce(p.code, '') = :keywordExact
                            or coalesce(p.code, '') like :keywordPrefix
                            or (:keywordId is not null and p.id = :keywordId)
                            or (:keywordId is not null and e.id = :keywordId)
                            or coalesce(e.code, '') = :keywordExact
                            or coalesce(e.code, '') like :keywordPrefix
                            or coalesce(appointment.code, '') = :keywordExact
                            or coalesce(appointment.code, '') like :keywordPrefix
                            or coalesce(e.patientPhoneSnapshot, '') = :keywordExact
                            or coalesce(e.patientPhoneSnapshot, '') like :keywordPrefix
                            or coalesce(patient.phone, '') = :keywordExact
                            or coalesce(patient.phone, '') like :keywordPrefix
                            or coalesce(appointment.patientPhone, '') = :keywordExact
                            or coalesce(appointment.patientPhone, '') like :keywordPrefix
                            or lower(coalesce(e.patientFullNameSnapshot, '')) like :keywordLike
                            or lower(coalesce(patient.fullName, '')) like :keywordLike
                            or lower(coalesce(patient.code, '')) like :keywordLike
                            or lower(coalesce(encounterDoctor.fullName, '')) like :keywordLike
                            or lower(coalesce(prescribingDoctor.fullName, '')) like :keywordLike
                            or lower(coalesce(appointment.patientFullName, '')) like :keywordLike
                      )
                    """
    )
    Page<Long> findIdsForPharmacy(
            @Param("status") PrescriptionStatus status,
            @Param("keywordLike") String keywordLike,
            @Param("keywordPrefix") String keywordPrefix,
            @Param("keywordExact") String keywordExact,
            @Param("keywordId") Long keywordId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"encounter", "doctorUser", "items", "items.medication"})
    @Query("select distinct p from Prescription p where p.id in :ids")
    List<Prescription> findAllWithDetailsByIdIn(@Param("ids") Collection<Long> ids);
}
