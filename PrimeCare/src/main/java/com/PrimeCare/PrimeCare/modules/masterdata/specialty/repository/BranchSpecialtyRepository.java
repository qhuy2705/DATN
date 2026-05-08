package com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository;

import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.BranchSpecialty;
import com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BranchSpecialtyRepository extends JpaRepository<BranchSpecialty, Long> {

    boolean existsByBranch_IdAndSpecialty_Id(Long branchId, Long specialtyId);

    boolean existsByBranch_IdAndSpecialty_IdAndStatus(
            Long branchId,
            Long specialtyId,
            BranchSpecialtyStatus status
    );

    Optional<BranchSpecialty> findByBranch_IdAndSpecialty_IdAndStatus(
            Long branchId,
            Long specialtyId,
            BranchSpecialtyStatus status
    );

    Page<BranchSpecialty> findByBranch_Id(Long branchId, Pageable pageable);

    @Query("""
            select bs
            from BranchSpecialty bs
            where bs.branch.id = :branchId
              and bs.branch.status = com.PrimeCare.PrimeCare.shared.enums.BranchStatus.ACTIVE
              and bs.status = com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus.ACTIVE
              and bs.specialty.status = 'ACTIVE'
            order by bs.displayOrder asc, bs.specialty.nameVn asc
            """)
    List<BranchSpecialty> findActiveByBranchId(Long branchId);

    @Query("""
        select bs
        from BranchSpecialty bs
        where (:branchId is null or bs.branch.id = :branchId)
          and (:status is null or bs.status = :status)
          and (
                :q is null
                or lower(bs.branch.nameVn) like lower(concat('%', :q, '%'))
                or lower(bs.specialty.code) like lower(concat('%', :q, '%'))
                or lower(bs.specialty.nameVn) like lower(concat('%', :q, '%'))
                or lower(coalesce(bs.specialty.nameEn, '')) like lower(concat('%', :q, '%'))
          )
        """)
    Page<BranchSpecialty> searchAdmin(@Param("branchId") Long branchId,
                                      @Param("q") String q,
                                      @Param("status") BranchSpecialtyStatus status,
                                      Pageable pageable);
}
