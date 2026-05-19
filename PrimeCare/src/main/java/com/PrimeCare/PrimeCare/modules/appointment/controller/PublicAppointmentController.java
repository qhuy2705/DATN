package com.PrimeCare.PrimeCare.modules.appointment.controller;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.CreateAppointmentRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/appointments")
@RequiredArgsConstructor
public class PublicAppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    public ApiResponse<AppointmentResponse> create(
            @Valid @RequestBody CreateAppointmentRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok("Đặt lịch thành công", appointmentService.create(request, authenticatedUserId(authentication)));
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
