package com.PrimeCare.PrimeCare.modules.appointment.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.*;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAdminResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentBookingRestrictionSummaryResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentStatusSummaryResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAdminService;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentBookingRestrictionSummaryService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/appointments")
@RequiredArgsConstructor
public class AdminAppointmentController {

    private final AppointmentAdminService appointmentAdminService;
    private final AppointmentBookingRestrictionSummaryService appointmentBookingRestrictionSummaryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<PageResponse<AppointmentAdminResponse>> search(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) LocalDate visitDate,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Boolean followUpPending,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10, sort = {"visitDate", "etaStart", "createdAt"}, direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.ok(
                "OK",
                appointmentAdminService.search(
                        status,
                        visitDate,
                        branchId,
                        doctorId,
                        specialtyId,
                        overdue,
                        followUpPending,
                        q,
                        PaginationConfig.withSort(
                                pageable,
                                Sort.by("visitDate").ascending()
                                    .and(Sort.by("etaStart").ascending())
                                    .and(Sort.by("createdAt").ascending())
                        )
                )
        );
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentStatusSummaryResponse> summary(
            @RequestParam(required = false) LocalDate visitDate,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long specialtyId
    ) {
        return ApiResponse.ok(
                "OK",
                appointmentAdminService.summary(visitDate, branchId, doctorId, specialtyId)
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok("OK", appointmentAdminService.getDetail(id));
    }

    @GetMapping("/{id}/booking-restriction-summary")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentBookingRestrictionSummaryResponse> bookingRestrictionSummary(@PathVariable Long id) {
        return ApiResponse.ok("OK", appointmentBookingRestrictionSummaryService.getSummary(id));
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> claim(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Đã nhận xử lý lịch hẹn.",
                appointmentAdminService.claim(id, currentUserId)
        );
    }

    @PostMapping("/{id}/heartbeat")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> heartbeat(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Gia hạn giữ quyền xử lý thành công",
                appointmentAdminService.heartbeat(id, currentUserId)
        );
    }

    @PostMapping("/{id}/release-claim")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> releaseClaim(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Đã nhả xử lý",
                appointmentAdminService.releaseClaim(id, currentUserId)
        );
    }

    @PatchMapping("/{id}/intake")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> updateIntake(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody UpdateAppointmentIntakeRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Cập nhật tiếp đón ban đầu thành công",
                appointmentAdminService.updateIntake(id, currentUserId, request)
        );
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> confirm(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody AppointmentConfirmRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Xác nhận lịch hẹn thành công",
                appointmentAdminService.confirm(id, currentUserId, request)
        );
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> cancel(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody(required = false) AppointmentCancelRequest request
    ) {
        requireCancelRequest(request);
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Đã hủy lịch hẹn",
                appointmentAdminService.cancel(
                        id,
                        currentUserId,
                        request.getReason(),
                        request.getCancellationReasonType(),
                        Boolean.TRUE.equals(request.getEnableRecoveryFlow()),
                        Boolean.TRUE.equals(request.getCountAsViolation()),
                        request.getViolationNote()
                )
        );
    }

    private void requireCancelRequest(AppointmentCancelRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Cancellation reason is required.");
        }
    }

    @PostMapping("/{id}/call-result")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> callResult(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody AppointmentCallResultRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Đã ghi nhận kết quả cuộc gọi",
                appointmentAdminService.recordCallResult(
                        id,
                        currentUserId,
                        request.getOutcome(),
                        request.getNote(),
                        Boolean.TRUE.equals(request.getSendFallbackEmail())
                )
        );
    }

    @PostMapping("/{id}/wrong-contact-violation")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> wrongContactViolation(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody WrongContactViolationRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Đã ghi nhận vi phạm sai thông tin liên hệ",
                appointmentAdminService.markWrongContactViolation(id, currentUserId, request.getReason())
        );
    }

    @PostMapping("/doctor-cancellation-recovery")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<List<AppointmentAdminResponse>> recoverDoctorCancellation(
            Authentication authentication,
            @Valid @RequestBody DoctorCancellationRecoveryRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Đã kích hoạt luồng hỗ trợ dời lịch cho các lịch bị ảnh hưởng",
                appointmentAdminService.recoverDoctorCancellationRange(currentUserId, request)
        );
    }

    @PostMapping("/check-in")
    @Deprecated(since = "2026-05", forRemoval = false)
    @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<AppointmentAdminResponse> checkIn(
            Authentication authentication,
            @Valid @RequestBody AppointmentCheckInRequest request
    ) {
        // Deprecated: QR check-in belongs to Reception. Use /api/reception/appointments/check-in/qr.
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Check-in thành công",
                appointmentAdminService.checkInByQrToken(request.getQrToken(), currentUserId)
        );
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> noShow(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody(required = false) AppointmentNoShowRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Đã đánh dấu bệnh nhân không đến",
                appointmentAdminService.markNoShow(id, currentUserId, request != null ? request.getNote() : null)
        );
    }

    @GetMapping("/{id}/reschedule-availability")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse> rescheduleAvailability(
            @PathVariable Long id,
            @RequestParam LocalDate visitDate,
            @RequestParam com.PrimeCare.PrimeCare.shared.enums.BranchSessionType session,
            @RequestParam(defaultValue = "true") boolean onlyAvailable
    ) {
        return ApiResponse.ok(
                "OK",
                appointmentAdminService.getRescheduleAvailability(id, visitDate, session, onlyAvailable)
        );
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> reschedule(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody AppointmentRescheduleRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());

        return ApiResponse.ok(
                "Đổi lịch hẹn thành công",
                appointmentAdminService.reschedule(id, currentUserId, request)
        );
    }

    @PostMapping("/{id}/resolve-no-show")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> resolveNoShow(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody(required = false) AppointmentNoShowRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());

        return ApiResponse.ok(
                "Đã xử lý follow-up cho lịch no-show",
                appointmentAdminService.resolveNoShowAsClosed(
                        id,
                        currentUserId,
                        request != null ? request.getNote() : null
                )
        );
    }

    @PostMapping("/{id}/resolve-follow-up")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> resolveFollowUp(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody(required = false) AppointmentNoShowRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());

        return ApiResponse.ok(
                "Đã xử lý follow-up cho lịch hẹn",
                appointmentAdminService.resolveFollowUpAsClosed(
                        id,
                        currentUserId,
                        request != null ? request.getNote() : null
                )
        );
    }
}
