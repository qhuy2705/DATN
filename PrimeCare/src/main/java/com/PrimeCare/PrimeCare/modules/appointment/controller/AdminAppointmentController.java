package com.PrimeCare.PrimeCare.modules.appointment.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.*;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAdminResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentStatusSummaryResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAdminService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/appointments")
@RequiredArgsConstructor
public class AdminAppointmentController {

    private final AppointmentAdminService appointmentAdminService;

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
                appointmentAdminService.confirm(id, currentUserId, request.getNote())
        );
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> cancel(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody AppointmentCancelRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Đã hủy lịch hẹn",
                appointmentAdminService.cancel(id, currentUserId, request.getReason())
        );
    }

    @PostMapping("/check-in")
    @PreAuthorize("hasAnyRole('OPERATIONS_ADMIN','STAFF')")
    public ApiResponse<AppointmentAdminResponse> checkIn(
            Authentication authentication,
            @Valid @RequestBody AppointmentCheckInRequest request
    ) {
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
}
