package com.PrimeCare.PrimeCare.modules.appointment.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAdminResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.DoctorAppointmentSummaryResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.DoctorAppointmentService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/doctor/appointments")
@RequiredArgsConstructor
public class DoctorAppointmentController {

    private final DoctorAppointmentService doctorAppointmentService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<DoctorAppointmentSummaryResponse> myAppointmentSummary(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate visitDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "OK",
                doctorAppointmentService.myAppointmentSummary(userId, visitDate, fromDate, toDate)
        );
    }

    @GetMapping("/waiting")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<PageResponse<AppointmentAdminResponse>> myWaitingAppointments(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate visitDate,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "OK",
                doctorAppointmentService.myWaitingAppointments(
                        userId,
                        visitDate,
                        PaginationConfig.withoutSort(pageable)
                )
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<PageResponse<AppointmentAdminResponse>> myAppointments(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 10, sort = {"visitDate", "etaStart"}, direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "OK",
                doctorAppointmentService.myAppointments(
                        userId,
                        from,
                        to,
                        PaginationConfig.withSort(
                                pageable,
                                Sort.by("visitDate").ascending().and(Sort.by("etaStart").ascending())
                        )
                )
        );
    }
}
