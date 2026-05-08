package com.PrimeCare.PrimeCare.modules.appointment.controller;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AvailabilityPreviewResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAvailabilityService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/public/availability")
@RequiredArgsConstructor
public class PublicAvailabilityController {

    private final AppointmentAvailabilityService appointmentAvailabilityService;

    @GetMapping
    public ApiResponse<AppointmentAvailabilityResponse> getAvailability(
            @RequestParam Long branchId,
            @RequestParam Long specialtyId,
            @RequestParam Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate visitDate,
            @RequestParam BranchSessionType session,
            @RequestParam(defaultValue = "false") boolean onlyAvailable
    ) {
        return ApiResponse.ok(
                "OK",
                appointmentAvailabilityService.getAvailability(branchId, specialtyId, doctorId, visitDate, session, onlyAvailable)
        );
    }

    @GetMapping("/preview")
    public ApiResponse<AvailabilityPreviewResponse> preview(
            @RequestParam Long branchId,
            @RequestParam Long specialtyId,
            @RequestParam Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(defaultValue = "7") int days
    ) {
        return ApiResponse.ok(
                "OK",
                appointmentAvailabilityService.getAvailabilityPreview(branchId, specialtyId, doctorId, fromDate, days)
        );
    }
}
