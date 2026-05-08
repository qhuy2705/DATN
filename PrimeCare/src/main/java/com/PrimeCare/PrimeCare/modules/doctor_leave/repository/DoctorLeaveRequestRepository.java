package com.PrimeCare.PrimeCare.modules.doctor_leave.repository;

import com.PrimeCare.PrimeCare.modules.doctor_leave.entity.DoctorLeaveRequest;
import com.PrimeCare.PrimeCare.shared.enums.DoctorLeaveRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DoctorLeaveRequestRepository extends JpaRepository<DoctorLeaveRequest, Long> {

    Page<DoctorLeaveRequest> findByDoctor_Id(Long doctorId, Pageable pageable);

    Page<DoctorLeaveRequest> findByDoctor_IdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long doctorId,
            LocalDate endDate,
            LocalDate startDate,
            Pageable pageable
    );

    Page<DoctorLeaveRequest> findByDoctor_IdAndStatus(
            Long doctorId,
            DoctorLeaveRequestStatus status,
            Pageable pageable
    );

    Page<DoctorLeaveRequest> findByDoctor_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long doctorId,
            DoctorLeaveRequestStatus status,
            LocalDate endDate,
            LocalDate startDate,
            Pageable pageable
    );

    Page<DoctorLeaveRequest> findByStatus(DoctorLeaveRequestStatus status, Pageable pageable);

    Page<DoctorLeaveRequest> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
            LocalDate endDate,
            LocalDate startDate,
            Pageable pageable
    );

    Page<DoctorLeaveRequest> findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            DoctorLeaveRequestStatus status,
            LocalDate endDate,
            LocalDate startDate,
            Pageable pageable
    );

    List<DoctorLeaveRequest> findByDoctor_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long doctorId,
            DoctorLeaveRequestStatus status,
            LocalDate endDate,
            LocalDate startDate
    );

    List<DoctorLeaveRequest> findByDoctor_IdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long doctorId,
            Collection<DoctorLeaveRequestStatus> statuses,
            LocalDate endDate,
            LocalDate startDate
    );
}
