package com.PrimeCare.PrimeCare.modules.staff.repository;

import com.PrimeCare.PrimeCare.modules.staff.dto.query.StaffStatusCountRow;
import com.PrimeCare.PrimeCare.modules.staff.entity.StaffProfile;
import com.PrimeCare.PrimeCare.shared.enums.StaffStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {
    Page<StaffProfile> findAllByBranch_Id(Long branchId, Pageable pageable);
    @Query("""
        select s
        from StaffProfile s
        where (:branchId is null or s.branch.id = :branchId)
          and (:status is null or s.status = :status)
          and (
                :q is null
                or lower(s.fullName) like lower(concat('%', :q, '%'))
                or lower(s.branch.nameVn) like lower(concat('%', :q, '%'))
          )
        """)
    Page<StaffProfile> searchAdmin(@Param("branchId") Long branchId,
                                   @Param("q") String q,
                                   @Param("status") StaffStatus status,
                                   Pageable pageable);

    @Query("""
        select s.status as status, count(s.id) as count
        from StaffProfile s
        where (:branchId is null or s.branch.id = :branchId)
          and (
                :q is null
                or lower(s.fullName) like lower(concat('%', :q, '%'))
                or lower(s.branch.nameVn) like lower(concat('%', :q, '%'))
          )
        group by s.status
        """)
    List<StaffStatusCountRow> countAdminSummary(@Param("branchId") Long branchId,
                                                @Param("q") String q);
}
