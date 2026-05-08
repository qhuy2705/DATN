package com.PrimeCare.PrimeCare.modules.doctor_leave.service;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.doctor_leave.dto.request.CreateDoctorLeaveRequest;
import com.PrimeCare.PrimeCare.modules.doctor_leave.dto.request.ReviewDoctorLeaveRequest;
import com.PrimeCare.PrimeCare.modules.doctor_leave.dto.response.DoctorLeaveRequestResponse;
import com.PrimeCare.PrimeCare.modules.doctor_leave.entity.DoctorLeaveRequest;
import com.PrimeCare.PrimeCare.modules.doctor_leave.repository.DoctorLeaveRequestRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.DoctorLeaveRequestStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorLeaveRequestService {

    private final DoctorLeaveRequestRepository doctorLeaveRequestRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;

    @Transactional
    public DoctorLeaveRequestResponse create(Long currentUserId, CreateDoctorLeaveRequest request) {
        User user = userRepository.findById(currentUserId).orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));
        DoctorProfile doctor = user.getDoctorProfile();
        if (doctor == null) {
            throw new ApiException(ErrorCode.DOCTOR_NOT_FOUND);
        }

        validateDateRange(request.getStartDate(), request.getEndDate());

        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Không thể tạo đơn nghỉ cho ngày trong quá khứ");
        }

        if (request.getStartDate().equals(request.getEndDate())
                && request.getStartSession().ordinal() > request.getEndSession().ordinal()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Buổi bắt đầu phải trước hoặc bằng buổi kết thúc");
        }

        validateLeaveOverlap(doctor.getId(), request.getStartDate(), request.getEndDate(), request.getStartSession(), request.getEndSession());

        DoctorLeaveRequest entity = DoctorLeaveRequest.builder()
                                                      .doctor(doctor)
                                                      .startDate(request.getStartDate())
                                                      .endDate(request.getEndDate())
                                                      .startSession(request.getStartSession())
                                                      .endSession(request.getEndSession())
                                                      .reason(request.getReason())
                                                      .status(DoctorLeaveRequestStatus.PENDING)
                                                      .build();

        return toResponse(doctorLeaveRequestRepository.save(entity));
    }

    public Page<DoctorLeaveRequestResponse> getMyRequests(
            Long currentUserId,
            LocalDate from,
            LocalDate to,
            DoctorLeaveRequestStatus status,
            int page,
            int size
    ) {
        User user = userRepository.findById(currentUserId).orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));
        DoctorProfile doctor = user.getDoctorProfile();
        if (doctor == null) {
            throw new ApiException(ErrorCode.DOCTOR_NOT_FOUND);
        }

        return findLeaveRequests(doctor.getId(), from, to, status, page, size)
                .map(this::toResponse);
    }

    @Transactional
    public DoctorLeaveRequestResponse cancel(Long id, Long currentUserId) {
        User user = userRepository.findById(currentUserId).orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));
        DoctorLeaveRequest entity = doctorLeaveRequestRepository.findById(id)
                                                                .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_LEAVE_REQUEST_NOT_FOUND));

        if (user.getDoctorProfile() == null || !entity.getDoctor().getId().equals(user.getDoctorProfile().getId())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        if (entity.getStatus() != DoctorLeaveRequestStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chỉ có thể hủy đơn nghỉ đang chờ duyệt");
        }

        entity.setStatus(DoctorLeaveRequestStatus.CANCELLED);
        return toResponse(doctorLeaveRequestRepository.save(entity));
    }

    public Page<DoctorLeaveRequestResponse> getAll(
            Long doctorId,
            DoctorLeaveRequestStatus status,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        return findLeaveRequests(doctorId, from, to, status, page, size)
                .map(this::toResponse);
    }

    @Transactional
    public DoctorLeaveRequestResponse approve(Long id, ReviewDoctorLeaveRequest request, Long currentUserId) {
        User user = userRepository.findById(currentUserId).orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));
        DoctorLeaveRequest entity = doctorLeaveRequestRepository.findById(id)
                                                                .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_LEAVE_REQUEST_NOT_FOUND));

        if (entity.getStatus() != DoctorLeaveRequestStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }

        if (hasAppointmentConflict(entity)) {
            throw new ApiException(ErrorCode.DOCTOR_LEAVE_HAS_APPOINTMENT_CONFLICT);
        }

        entity.setStatus(DoctorLeaveRequestStatus.APPROVED);
        entity.setReviewedBy(user);
        entity.setReviewedAt(LocalDateTime.now());
        entity.setReviewNote(request.getReviewNote());

        return toResponse(doctorLeaveRequestRepository.save(entity));
    }

    @Transactional
    public DoctorLeaveRequestResponse reject(Long id, ReviewDoctorLeaveRequest request, Long currentUserId) {
        User user = userRepository.findById(currentUserId).orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));
        DoctorLeaveRequest entity = doctorLeaveRequestRepository.findById(id)
                                                                .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_LEAVE_REQUEST_NOT_FOUND));

        if (entity.getStatus() != DoctorLeaveRequestStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }

        entity.setStatus(DoctorLeaveRequestStatus.REJECTED);
        entity.setReviewedBy(user);
        entity.setReviewedAt(LocalDateTime.now());
        entity.setReviewNote(request.getReviewNote());

        return toResponse(doctorLeaveRequestRepository.save(entity));
    }

    public boolean hasApprovedLeaveForSession(Long doctorId, LocalDate workDate, BranchSessionType session) {
        return doctorLeaveRequestRepository
                .findByDoctor_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        doctorId,
                        DoctorLeaveRequestStatus.APPROVED,
                        workDate,
                        workDate
                )
                .stream()
                .anyMatch(leave -> isSessionCovered(
                        workDate,
                        session,
                        leave.getStartDate(),
                        leave.getEndDate(),
                        leave.getStartSession(),
                        leave.getEndSession()
                ));
    }

    private Page<DoctorLeaveRequest> findLeaveRequests(
            Long doctorId,
            LocalDate from,
            LocalDate to,
            DoctorLeaveRequestStatus status,
            int page,
            int size
    ) {
        var pageable = PaginationConfig.pageRequest(page, size, Sort.by("startDate").descending().and(Sort.by("id").descending()));

        if (from != null && to != null) {
            validateDateRange(from, to);

            if (doctorId != null && status != null) {
                return doctorLeaveRequestRepository.findByDoctor_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        doctorId,
                        status,
                        to,
                        from,
                        pageable
                );
            }
            if (doctorId != null) {
                return doctorLeaveRequestRepository.findByDoctor_IdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        doctorId,
                        to,
                        from,
                        pageable
                );
            }
            if (status != null) {
                return doctorLeaveRequestRepository.findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        status,
                        to,
                        from,
                        pageable
                );
            }
            return doctorLeaveRequestRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
                    to,
                    from,
                    pageable
            );
        }

        if (doctorId != null && status != null) {
            return doctorLeaveRequestRepository.findByDoctor_IdAndStatus(doctorId, status, pageable);
        }
        if (doctorId != null) {
            return doctorLeaveRequestRepository.findByDoctor_Id(doctorId, pageable);
        }
        if (status != null) {
            return doctorLeaveRequestRepository.findByStatus(status, pageable);
        }
        return doctorLeaveRequestRepository.findAll(pageable);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Ngày bắt đầu và ngày kết thúc là bắt buộc");
        }
        if (startDate.isAfter(endDate)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Ngày bắt đầu không được sau ngày kết thúc");
        }
    }

    private void validateLeaveOverlap(
            Long doctorId,
            LocalDate startDate,
            LocalDate endDate,
            BranchSessionType startSession,
            BranchSessionType endSession
    ) {
        List<DoctorLeaveRequest> overlaps = doctorLeaveRequestRepository
                .findByDoctor_IdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        doctorId,
                        EnumSet.of(DoctorLeaveRequestStatus.PENDING, DoctorLeaveRequestStatus.APPROVED),
                        endDate,
                        startDate
                );

        boolean hasOverlap = overlaps.stream().anyMatch(existing -> hasLeaveSessionOverlap(
                existing.getStartDate(),
                existing.getEndDate(),
                existing.getStartSession(),
                existing.getEndSession(),
                startDate,
                endDate,
                startSession,
                endSession
        ));

        if (hasOverlap) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Đã có đơn nghỉ trùng thời gian đang chờ duyệt hoặc đã được duyệt");
        }
    }

    private boolean hasLeaveSessionOverlap(
            LocalDate firstStartDate,
            LocalDate firstEndDate,
            BranchSessionType firstStartSession,
            BranchSessionType firstEndSession,
            LocalDate secondStartDate,
            LocalDate secondEndDate,
            BranchSessionType secondStartSession,
            BranchSessionType secondEndSession
    ) {
        LocalDate current = firstStartDate.isAfter(secondStartDate) ? firstStartDate : secondStartDate;
        LocalDate end = firstEndDate.isBefore(secondEndDate) ? firstEndDate : secondEndDate;

        while (!current.isAfter(end)) {
            for (BranchSessionType session : BranchSessionType.values()) {
                boolean firstCovered = isSessionCovered(current, session, firstStartDate, firstEndDate, firstStartSession, firstEndSession);
                boolean secondCovered = isSessionCovered(current, session, secondStartDate, secondEndDate, secondStartSession, secondEndSession);
                if (firstCovered && secondCovered) {
                    return true;
                }
            }
            current = current.plusDays(1);
        }
        return false;
    }

    private boolean hasAppointmentConflict(DoctorLeaveRequest entity) {
        List<Appointment> appointments = appointmentRepository
                .findByDoctor_IdAndVisitDateBetweenAndStatusIn(
                        entity.getDoctor().getId(),
                        entity.getStartDate(),
                        entity.getEndDate(),
                        List.of(AppointmentStatus.REQUESTED, AppointmentStatus.CONFIRMED)
                );

        for (Appointment appointment : appointments) {
            if (isSessionCovered(
                    appointment.getVisitDate(),
                    appointment.getSession(),
                    entity.getStartDate(),
                    entity.getEndDate(),
                    entity.getStartSession(),
                    entity.getEndSession()
            )) {
                return true;
            }
        }
        return false;
    }

    public boolean isSessionCovered(
            LocalDate targetDate,
            BranchSessionType targetSession,
            LocalDate startDate,
            LocalDate endDate,
            BranchSessionType startSession,
            BranchSessionType endSession
    ) {
        if (targetDate.isBefore(startDate) || targetDate.isAfter(endDate)) {
            return false;
        }

        if (startDate.equals(endDate)) {
            return targetSession.ordinal() >= startSession.ordinal()
                    && targetSession.ordinal() <= endSession.ordinal();
        }

        if (targetDate.equals(startDate)) {
            return targetSession.ordinal() >= startSession.ordinal();
        }

        if (targetDate.equals(endDate)) {
            return targetSession.ordinal() <= endSession.ordinal();
        }

        return true;
    }

    private DoctorLeaveRequestResponse toResponse(DoctorLeaveRequest entity) {
        return DoctorLeaveRequestResponse.builder()
                                         .id(entity.getId())
                                         .doctorId(entity.getDoctor().getId())
                                         .doctorName(entity.getDoctor().getFullName() != null ? entity.getDoctor().getFullName() : null)
                                         .startDate(entity.getStartDate())
                                         .endDate(entity.getEndDate())
                                         .startSession(entity.getStartSession())
                                         .endSession(entity.getEndSession())
                                         .reason(entity.getReason())
                                         .status(entity.getStatus())
                                         .reviewNote(entity.getReviewNote())
                                         .reviewedAt(entity.getReviewedAt())
                                         .reviewedByName(resolveDisplayName(entity.getReviewedBy()))
                                         .build();
    }

    private String resolveDisplayName(User user) {
        if (user == null) return null;
        if (user.getAdminProfile() != null && user.getAdminProfile().getFullName() != null) {
            return user.getAdminProfile().getFullName();
        }
        if (user.getStaffProfile() != null && user.getStaffProfile().getFullName() != null) {
            return user.getStaffProfile().getFullName();
        }
        if (user.getDoctorProfile() != null && user.getDoctorProfile().getFullName() != null) {
            return user.getDoctorProfile().getFullName();
        }
        return user.getEmail();
    }
}
