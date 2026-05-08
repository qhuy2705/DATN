package com.PrimeCare.PrimeCare.modules.masterdata.branch.repository;

import com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.query.BranchStatusCountRow;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    boolean existsByCode(String code);
    Page<Branch> findAllByStatus(BranchStatus status, Pageable pageable);
    @Query("""
        select b
        from Branch b
        where (:status is null or b.status = :status)
          and (
                :q is null
                or lower(b.code) like lower(concat('%', :q, '%'))
                or lower(b.nameVn) like lower(concat('%', :q, '%'))
                or lower(coalesce(b.nameEn, '')) like lower(concat('%', :q, '%'))
                or lower(b.addressVn) like lower(concat('%', :q, '%'))
                or lower(coalesce(b.addressEn, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(b.phone, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(b.email, '')) like lower(concat('%', :q, '%'))
          )
        """)
    Page<Branch> searchAdmin(@Param("q") String q,
                             @Param("status") BranchStatus status,
                             Pageable pageable);

    @Query("""
        select b.status as status, count(b.id) as count
        from Branch b
        where (
                :q is null
                or lower(b.code) like lower(concat('%', :q, '%'))
                or lower(b.nameVn) like lower(concat('%', :q, '%'))
                or lower(coalesce(b.nameEn, '')) like lower(concat('%', :q, '%'))
                or lower(b.addressVn) like lower(concat('%', :q, '%'))
                or lower(coalesce(b.addressEn, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(b.phone, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(b.email, '')) like lower(concat('%', :q, '%'))
          )
        group by b.status
        """)
    List<BranchStatusCountRow> countAdminSummary(@Param("q") String q);
}
