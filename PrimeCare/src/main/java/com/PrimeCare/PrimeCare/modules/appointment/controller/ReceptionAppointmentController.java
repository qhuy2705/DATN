package com.PrimeCare.PrimeCare.modules.appointment.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.AppointmentCheckInRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.CreateWalkInAppointmentRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAdminResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.ReceptionQueueSummaryResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAdminService;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentReceptionService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reception/appointments")
@RequiredArgsConstructor
public class ReceptionAppointmentController {

    private final AppointmentReceptionService appointmentReceptionService;
    private final AppointmentAdminService appointmentAdminService;

    @PostMapping("/walk-in")
    @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<AppointmentAdminResponse> createWalkIn(
            Authentication authentication,
            @Valid @RequestBody CreateWalkInAppointmentRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Tạo lượt khám walk-in thành công",
                appointmentReceptionService.createWalkIn(request, currentUserId)
        );
    }

    @PostMapping("/{id}/arrive")
    @Deprecated(since = "2026-05", forRemoval = false)
    @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<AppointmentAdminResponse> markArrived(
            @PathVariable Long id,
            Authentication authentication
    ) {
        // Deprecated: Check-in is the primary reception action and already implies arrival.
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Đã đánh dấu bệnh nhân đã đến",
                appointmentReceptionService.markArrived(id, currentUserId)
        );
    }

    @PostMapping("/check-in/qr")
    @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<AppointmentAdminResponse> qrCheckIn(
            Authentication authentication,
            @Valid @RequestBody AppointmentCheckInRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Check-in QR thành công",
                appointmentAdminService.checkInByQrToken(request.getQrToken(), currentUserId)
        );
    }

    @PostMapping("/{id}/manual-check-in")
    @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<AppointmentAdminResponse> manualCheckIn(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "Check-in thủ công thành công",
                appointmentReceptionService.manualCheckIn(id, currentUserId)
        );
    }

    @GetMapping("/queue")
    @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<PageResponse<AppointmentAdminResponse>> queue(
            @RequestParam(required = false) LocalDate visitDate,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) ArrivalStatus arrivalStatus,
            @RequestParam(required = false) AppointmentSourceType sourceType,
            @RequestParam(required = false) String triagePriority,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ApiResponse.ok(
                "OK",
                appointmentReceptionService.queue(
                        visitDate,
                        branchId,
                        doctorId,
                        specialtyId,
                        arrivalStatus,
                        sourceType,
                        triagePriority,
                        overdue,
                        q,
                        PaginationConfig.withoutSort(pageable)
                )
        );
    }

    @GetMapping("/queue/summary")
    @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<ReceptionQueueSummaryResponse> queueSummary(
            @RequestParam(required = false) LocalDate visitDate,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) ArrivalStatus arrivalStatus,
            @RequestParam(required = false) AppointmentSourceType sourceType,
            @RequestParam(required = false) String triagePriority,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) String q
    ) {
        return ApiResponse.ok(
                "OK",
                appointmentReceptionService.queueSummary(
                        visitDate,
                        branchId,
                        doctorId,
                        specialtyId,
                        arrivalStatus,
                        sourceType,
                        triagePriority,
                        overdue,
                        q
                )
        );
    }
}
