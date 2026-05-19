package com.PrimeCare.PrimeCare.modules.appointment.controller;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.PublicRescheduleOfferResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.PublicRescheduleService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/reschedule")
@RequiredArgsConstructor
public class PublicRescheduleController {

    private final PublicRescheduleService publicRescheduleService;

    @GetMapping("/{token}")
    public ApiResponse<PublicRescheduleOfferResponse> getOffer(@PathVariable String token) {
        return ApiResponse.ok("OK", publicRescheduleService.getOffer(token));
    }

    @PostMapping("/{token}/accept")
    public ApiResponse<PublicRescheduleOfferResponse> accept(@PathVariable String token) {
        return ApiResponse.ok("Đã xác nhận lịch mới", publicRescheduleService.accept(token));
    }

    @PostMapping("/{token}/request-contact")
    public ApiResponse<PublicRescheduleOfferResponse> requestContact(@PathVariable String token) {
        return ApiResponse.ok("Đã ghi nhận yêu cầu liên hệ", publicRescheduleService.requestContact(token));
    }

    @PostMapping("/{token}/cancel")
    public ApiResponse<PublicRescheduleOfferResponse> cancel(@PathVariable String token) {
        return ApiResponse.ok("Đã hủy slot giữ tạm", publicRescheduleService.cancel(token));
    }
}
