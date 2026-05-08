package com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository;

import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {

    boolean existsByCode(String code);

    Page<Specialty> findAllByStatus(String status, Pageable pageable);

    @Query("""
        select s
        from Specialty s
        where (:status is null or s.status = :status)
          and (
                :q is null
                or lower(s.code) like lower(concat('%', :q, '%'))
                or lower(s.nameVn) like lower(concat('%', :q, '%'))
                or lower(coalesce(s.nameEn, '')) like lower(concat('%', :q, '%'))
          )
        """)
    Page<Specialty> searchAdmin(@Param("q") String q,
                                @Param("status") String status,
                                Pageable pageable);
}