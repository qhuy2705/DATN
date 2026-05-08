package com.PrimeCare.PrimeCare.modules.medical_service.repository;

import com.PrimeCare.PrimeCare.modules.medical_service.entity.MedicalService;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicalServiceRepository extends JpaRepository<MedicalService, Long> {

    Optional<MedicalService> findByCode(String code);

    boolean existsByCode(String code);

    List<MedicalService> findByPublicVisibleTrueAndStatusOrderByDisplayOrderAscNameVnAsc(
            MedicalServiceStatus status
    );

    @Query("""
        select m
        from MedicalService m
        where (:status is null or m.status = :status)
          and (:publicVisible is null or m.publicVisible = :publicVisible)
          and (
                :q is null
                or lower(m.code) like lower(concat('%', :q, '%'))
                or lower(m.nameVn) like lower(concat('%', :q, '%'))
                or lower(coalesce(m.nameEn, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(m.descriptionVn, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(m.descriptionEn, '')) like lower(concat('%', :q, '%'))
                or lower(cast(m.serviceType as string)) like lower(concat('%', :q, '%'))
                or lower(coalesce(m.departmentCode, '')) like lower(concat('%', :q, '%'))
          )
        """)
    Page<MedicalService> searchAdmin(@Param("q") String q,
                                     @Param("status") MedicalServiceStatus status,
                                     @Param("publicVisible") Boolean publicVisible,
                                     Pageable pageable);
}
