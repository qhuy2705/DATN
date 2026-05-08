package com.PrimeCare.PrimeCare.modules.doctor_leave.controller;

import com.PrimeCare.PrimeCare.modules.doctor_leave.dto.request.ReviewDoctorLeaveRequest;
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
@RequestMapping("/api/admin/doctor-leaves")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATIONS_ADMIN')")
public class AdminDoctorLeaveController {

    private final DoctorLeaveRequestService doctorLeaveRequestService;

    @GetMapping
    public Page<DoctorLeaveRequestResponse> getAll(
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) DoctorLeaveRequestStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return doctorLeaveRequestService.getAll(doctorId, status, from, to, page, size);
    }

    @PatchMapping("/{id}/approve")
    public DoctorLeaveRequestResponse approve(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid ReviewDoctorLeaveRequest request,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        if (request == null) {
            request = new ReviewDoctorLeaveRequest();
        }
        return doctorLeaveRequestService.approve(id, request, currentUserId);
    }

    @PatchMapping("/{id}/reject")
    public DoctorLeaveRequestResponse reject(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid ReviewDoctorLeaveRequest request,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        if (request == null) {
            request = new ReviewDoctorLeaveRequest();
        }
        return doctorLeaveRequestService.reject(id, request, currentUserId);
    }
}
