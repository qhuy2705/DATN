package com.PrimeCare.PrimeCare.modules.staff.repository;

import com.PrimeCare.PrimeCare.modules.staff.dto.query.StaffAdminSummaryRow;
import com.PrimeCare.PrimeCare.modules.staff.dto.query.StaffStatusCountRow;
import com.PrimeCare.PrimeCare.modules.staff.entity.StaffProfile;
import com.PrimeCare.PrimeCare.shared.enums.StaffStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {
    Page<StaffProfile> findAllByBranch_Id(Long branchId, Pageable pageable);

    default Page<StaffProfile> searchAdmin(Long branchId,
                                           String q,
                                           StaffStatus status,
                                           Pageable pageable) {
        return searchAdmin(branchId, q, status, null, pageable);
    }

    @Query("""
        select distinct s
        from StaffProfile s
        left join User u on u.staffProfile = s
        where (:branchId is null or s.branch.id = :branchId)
          and (:status is null or s.status = :status)
          and (:role is null or u.role = :role)
          and (
                :q is null
                or lower(s.fullName) like lower(concat('%', :q, '%'))
                or lower(s.branch.nameVn) like lower(concat('%', :q, '%'))
          )
        """)
    Page<StaffProfile> searchAdmin(@Param("branchId") Long branchId,
                                   @Param("q") String q,
                                   @Param("status") StaffStatus status,
                                   @Param("role") UserRole role,
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

    default StaffAdminSummaryRow summarizeAdmin(Long branchId, String q, StaffStatus status) {
        return summarizeAdmin(branchId, q, status, null);
    }

    @Query("""
        select
          count(s.id) as total,
          count(case
                when s.status = com.PrimeCare.PrimeCare.shared.enums.StaffStatus.ACTIVE
                then s.id else null
          end) as active,
          count(case
                when s.status <> com.PrimeCare.PrimeCare.shared.enums.StaffStatus.ACTIVE
                then s.id else null
          end) as inactive,
          count(case
                when u.id is null
                then s.id else null
          end) as noAccountStaffs,
          count(case
                when u.id is not null
                 and u.status <> com.PrimeCare.PrimeCare.shared.enums.UserStatus.ACTIVE
                then s.id else null
          end) as inactiveAccountStaffs
        from StaffProfile s
        left join User u on u.staffProfile = s
        where (:branchId is null or s.branch.id = :branchId)
          and (:status is null or s.status = :status)
          and (:role is null or u.role = :role)
          and (
                :q is null
                or lower(s.fullName) like lower(concat('%', :q, '%'))
                or lower(s.branch.nameVn) like lower(concat('%', :q, '%'))
          )
        """)
    StaffAdminSummaryRow summarizeAdmin(@Param("branchId") Long branchId,
                                        @Param("q") String q,
                                        @Param("status") StaffStatus status,
                                        @Param("role") UserRole role);
}
