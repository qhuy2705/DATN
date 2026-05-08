package com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository;

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
    left join d.doctorSpecialties ds
    where (d.status = :activeStatus or d.status is null)
      and d.branch.status = com.PrimeCare.PrimeCare.shared.enums.BranchStatus.ACTIVE
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
}
