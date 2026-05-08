package com.PrimeCare.PrimeCare.modules.doctor_leave.controller;

import com.PrimeCare.PrimeCare.modules.doctor_leave.dto.request.CreateDoctorLeaveRequest;
import com.PrimeCare.PrimeCare.modules.doctor_leave.dto.response.DoctorLeaveRequestResponse;
import com.PrimeCare.PrimeCare.modules.doctor_leave.service.DoctorLeaveRequestService;
import com.PrimeCare.PrimeCare.shared.enums.DoctorLeaveRequestStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/doctor/leave-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorLeaveController {

    private final DoctorLeaveRequestService doctorLeaveRequestService;

    @PostMapping
    public DoctorLeaveRequestResponse create(
            Authentication authentication,
            @RequestBody @Valid CreateDoctorLeaveRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return doctorLeaveRequestService.create(currentUserId, request);
    }

    @GetMapping
    public Page<DoctorLeaveRequestResponse> getMyRequests(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) DoctorLeaveRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return doctorLeaveRequestService.getMyRequests(currentUserId, from, to, status, page, size);
    }

    @PatchMapping("/{id}/cancel")
    public DoctorLeaveRequestResponse cancel(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return doctorLeaveRequestService.cancel(id, currentUserId);
    }
}
