package com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository;

import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.query.DoctorAdminSummaryRow;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.query.DoctorStatusCountRow;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, Long> {
    Page<DoctorProfile> findAllByBranch_Id(Long branchId, Pageable pageable);

    @Query("""
    select distinct d
    from DoctorProfile d
    join User u on u.doctorProfile = d
    left join d.doctorSpecialties ds
    where (d.status = :activeStatus or d.status is null)
      and d.branch.status = com.PrimeCare.PrimeCare.shared.enums.BranchStatus.ACTIVE
      and u.role = com.PrimeCare.PrimeCare.shared.enums.UserRole.DOCTOR
      and u.status = com.PrimeCare.PrimeCare.shared.enums.UserStatus.ACTIVE
      and (:branchId is null or d.branch.id = :branchId)
      and (
            :specialtyId is null
            or (
                ds.specialty.id = :specialtyId
                and ds.specialty.status = 'ACTIVE'
                and exists (
                    select 1
                    from BranchSpecialty bs
                    where bs.branch = d.branch
                      and bs.specialty = ds.specialty
                      and bs.status = com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus.ACTIVE
                      and bs.branch.status = com.PrimeCare.PrimeCare.shared.enums.BranchStatus.ACTIVE
                )
            )
      )
      and (
            :q is null
            or lower(d.fullName) like lower(concat('%', :q, '%'))
            or lower(coalesce(d.displayTitleVn, '')) like lower(concat('%', :q, '%'))
            or lower(coalesce(d.displayTitleEn, '')) like lower(concat('%', :q, '%'))
            or lower(d.branch.nameVn) like lower(concat('%', :q, '%'))
            or lower(coalesce(d.branch.nameEn, '')) like lower(concat('%', :q, '%'))
            or lower(coalesce(ds.specialty.nameVn, '')) like lower(concat('%', :q, '%'))
            or lower(coalesce(ds.specialty.nameEn, '')) like lower(concat('%', :q, '%'))
      )
""")
    Page<DoctorProfile> search(
            @Param("branchId") Long branchId,
            @Param("specialtyId") Long specialtyId,
            @Param("q") String q,
            @Param("activeStatus") DoctorStatus activeStatus,
            Pageable pageable
    );

    @Query("""
    select distinct d
    from DoctorProfile d
    join User u on u.doctorProfile = d
    where d.id = :id
      and (d.status = com.PrimeCare.PrimeCare.shared.enums.DoctorStatus.ACTIVE or d.status is null)
      and d.branch.status = com.PrimeCare.PrimeCare.shared.enums.BranchStatus.ACTIVE
      and u.role = com.PrimeCare.PrimeCare.shared.enums.UserRole.DOCTOR
      and u.status = com.PrimeCare.PrimeCare.shared.enums.UserStatus.ACTIVE
""")
    Optional<DoctorProfile> findPublicBookableById(@Param("id") Long id);

    @Query("""
            select count(ds) > 0
            from DoctorSpecialty ds
            where ds.doctor.id = :doctorId and ds.specialty.id = :specialtyId
            """)
    boolean existsDoctorSpecialty(@Param("doctorId") Long doctorId, @Param("specialtyId") Long specialtyId);

    Optional<DoctorProfile> findByIdAndBranch_Id(Long id, Long branchId);

    @Query("""
        select distinct d
        from DoctorProfile d
        left join d.doctorSpecialties ds
        where (:branchId is null or d.branch.id = :branchId)
          and (:status is null or d.status = :status)
          and (:specialtyId is null or ds.specialty.id = :specialtyId)
          and (
                :q is null
                or lower(d.fullName) like lower(concat('%', :q, '%'))
                or lower(coalesce(d.displayTitleVn, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(d.displayTitleEn, '')) like lower(concat('%', :q, '%'))
                or lower(d.branch.nameVn) like lower(concat('%', :q, '%'))
                or lower(coalesce(ds.specialty.nameVn, '')) like lower(concat('%', :q, '%'))
          )
        """)
    Page<DoctorProfile> searchAdmin(@Param("branchId") Long branchId,
                                    @Param("specialtyId") Long specialtyId,
                                    @Param("q") String q,
                                    @Param("status") DoctorStatus status,
                                    Pageable pageable);

    @Query("""
        select distinct d
        from DoctorProfile d
        join fetch d.branch b
        left join fetch d.doctorSpecialties fetchedDoctorSpecialty
        left join fetch fetchedDoctorSpecialty.specialty fetchedSpecialty
        left join d.doctorSpecialties filterDoctorSpecialty
        left join filterDoctorSpecialty.specialty filterSpecialty
        where (:branchId is null or b.id = :branchId)
          and (:status is null or d.status = :status)
          and (:specialtyId is null or filterSpecialty.id = :specialtyId)
          and (
                :q is null
                or lower(d.fullName) like lower(concat('%', :q, '%'))
                or lower(coalesce(d.displayTitleVn, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(d.displayTitleEn, '')) like lower(concat('%', :q, '%'))
                or lower(b.nameVn) like lower(concat('%', :q, '%'))
                or lower(coalesce(b.nameEn, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(filterSpecialty.nameVn, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(filterSpecialty.nameEn, '')) like lower(concat('%', :q, '%'))
          )
        order by d.fullName asc, d.id asc
        """)
    List<DoctorProfile> findOptions(@Param("branchId") Long branchId,
                                    @Param("specialtyId") Long specialtyId,
                                    @Param("q") String q,
                                    @Param("status") DoctorStatus status);

    @Query("""
        select d.status as status, count(distinct d.id) as count
        from DoctorProfile d
        left join d.doctorSpecialties ds
        where (:branchId is null or d.branch.id = :branchId)
          and (:specialtyId is null or ds.specialty.id = :specialtyId)
          and (
                :q is null
                or lower(d.fullName) like lower(concat('%', :q, '%'))
                or lower(coalesce(d.displayTitleVn, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(d.displayTitleEn, '')) like lower(concat('%', :q, '%'))
                or lower(d.branch.nameVn) like lower(concat('%', :q, '%'))
                or lower(coalesce(ds.specialty.nameVn, '')) like lower(concat('%', :q, '%'))
          )
        group by d.status
        """)
    List<DoctorStatusCountRow> countAdminSummary(@Param("branchId") Long branchId,
                                                 @Param("specialtyId") Long specialtyId,
                                                 @Param("q") String q);

    @Query("""
        select
          count(distinct d.id) as total,
          count(distinct case
                when d.status = com.PrimeCare.PrimeCare.shared.enums.DoctorStatus.ACTIVE
                then d.id else null
          end) as active,
          count(distinct case
                when d.status <> com.PrimeCare.PrimeCare.shared.enums.DoctorStatus.ACTIVE
                then d.id else null
          end) as inactive,
          count(distinct case
                when u.id is null
                then d.id else null
          end) as noAccountDoctors,
          count(distinct case
                when u.id is not null
                 and u.status <> com.PrimeCare.PrimeCare.shared.enums.UserStatus.ACTIVE
                then d.id else null
          end) as inactiveAccountDoctors,
          count(distinct case
                when d.status = com.PrimeCare.PrimeCare.shared.enums.DoctorStatus.ACTIVE
                 and d.branch.status = com.PrimeCare.PrimeCare.shared.enums.BranchStatus.ACTIVE
                 and u.id is not null
                 and u.role = com.PrimeCare.PrimeCare.shared.enums.UserRole.DOCTOR
                 and u.status = com.PrimeCare.PrimeCare.shared.enums.UserStatus.ACTIVE
                then d.id else null
          end) as operationalReadyDoctors
        from DoctorProfile d
        left join User u on u.doctorProfile = d
        left join d.doctorSpecialties ds
        where (:branchId is null or d.branch.id = :branchId)
          and (:status is null or d.status = :status)
          and (:specialtyId is null or ds.specialty.id = :specialtyId)
          and (
                :q is null
                or lower(d.fullName) like lower(concat('%', :q, '%'))
                or lower(coalesce(d.displayTitleVn, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(d.displayTitleEn, '')) like lower(concat('%', :q, '%'))
                or lower(d.branch.nameVn) like lower(concat('%', :q, '%'))
                or lower(coalesce(ds.specialty.nameVn, '')) like lower(concat('%', :q, '%'))
          )
        """)
    DoctorAdminSummaryRow summarizeAdmin(@Param("branchId") Long branchId,
                                         @Param("specialtyId") Long specialtyId,
                                         @Param("q") String q,
                                         @Param("status") DoctorStatus status);

    @Query("""
            select distinct d
            from DoctorProfile d
            join User u on u.doctorProfile = d
            join d.doctorSpecialties ds
            where d.branch.id = :branchId
              and ds.specialty.id = :specialtyId
              and (d.status = com.PrimeCare.PrimeCare.shared.enums.DoctorStatus.ACTIVE or d.status is null)
              and d.branch.status = com.PrimeCare.PrimeCare.shared.enums.BranchStatus.ACTIVE
              and ds.specialty.status = 'ACTIVE'
              and u.role = com.PrimeCare.PrimeCare.shared.enums.UserRole.DOCTOR
              and u.status = com.PrimeCare.PrimeCare.shared.enums.UserStatus.ACTIVE
              and exists (
                  select 1
                  from BranchSpecialty bs
                  where bs.branch = d.branch
                    and bs.specialty = ds.specialty
                    and bs.status = com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus.ACTIVE
                    and bs.branch.status = com.PrimeCare.PrimeCare.shared.enums.BranchStatus.ACTIVE
              )
            order by d.id asc
            """)
    List<DoctorProfile> findActiveByBranchAndSpecialty(
            @Param("branchId") Long branchId,
            @Param("specialtyId") Long specialtyId
    );
}
