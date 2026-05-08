package com.PrimeCare.PrimeCare.modules.encounter.repository;

import com.PrimeCare.PrimeCare.modules.encounter.entity.Icd10Code;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface Icd10CodeRepository extends JpaRepository<Icd10Code, Long> {

    Optional<Icd10Code> findByCode(String code);

    boolean existsByCode(String code);

    @Query("""
            select c from Icd10Code c
            where c.active = true
              and (
                    lower(c.code) like lower(concat('%', :q, '%'))
                 or lower(c.nameVn) like lower(concat('%', :q, '%'))
                 or lower(c.nameEn) like lower(concat('%', :q, '%'))
              )
            order by c.code asc
            """)
    Page<Icd10Code> search(@Param("q") String q, Pageable pageable);

    List<Icd10Code> findByActiveTrue();

    List<Icd10Code> findByCategory(String category);

    List<Icd10Code> findByIdIn(List<Long> ids);
}
