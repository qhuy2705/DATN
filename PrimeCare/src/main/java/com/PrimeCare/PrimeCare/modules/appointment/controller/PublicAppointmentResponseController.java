package com.PrimeCare.PrimeCare.modules.appointment.controller;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.AppointmentResponseUpdatePhoneRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentResponseInfoResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentResponseService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/appointment-response")
@RequiredArgsConstructor
public class PublicAppointmentResponseController {

    private final AppointmentResponseService appointmentResponseService;

    @GetMapping("/{token}")
    public ApiResponse<AppointmentResponseInfoResponse> inspect(@PathVariable String token) {
        return ApiResponse.ok("OK", appointmentResponseService.inspect(token));
    }

    @PostMapping("/{token}/keep")
    public ApiResponse<AppointmentResponseInfoResponse> keep(@PathVariable String token) {
        return ApiResponse.ok("Đã ghi nhận yêu cầu giữ lịch hẹn", appointmentResponseService.keep(token));
    }

    @PostMapping("/{token}/request-recall")
    public ApiResponse<AppointmentResponseInfoResponse> requestRecall(@PathVariable String token) {
        return ApiResponse.ok("Đã ghi nhận yêu cầu nhân viên gọi lại", appointmentResponseService.requestRecall(token));
    }

    @PostMapping("/{token}/update-phone")
    public ApiResponse<AppointmentResponseInfoResponse> updatePhone(
            @PathVariable String token,
            @Valid @RequestBody AppointmentResponseUpdatePhoneRequest request
    ) {
        return ApiResponse.ok("Đã cập nhật số điện thoại", appointmentResponseService.updatePhone(token, request.getPhone()));
    }

    @PostMapping("/{token}/cancel")
    public ApiResponse<AppointmentResponseInfoResponse> cancel(@PathVariable String token) {
        return ApiResponse.ok("Đã hủy lịch hẹn", appointmentResponseService.cancel(token));
    }
}
