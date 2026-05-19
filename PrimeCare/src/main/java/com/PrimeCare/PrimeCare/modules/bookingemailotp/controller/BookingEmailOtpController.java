package com.PrimeCare.PrimeCare.modules.bookingemailotp.controller;

import com.PrimeCare.PrimeCare.modules.bookingemailotp.dto.request.BookingEmailOtpRequest;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.dto.request.BookingEmailOtpVerifyRequest;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.dto.response.BookingEmailOtpRequestResponse;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.dto.response.BookingEmailOtpVerifyResponse;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.service.BookingEmailOtpService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/booking-email-otp")
@RequiredArgsConstructor
public class BookingEmailOtpController {

    private final BookingEmailOtpService bookingEmailOtpService;

    @PostMapping("/request")
    public ApiResponse<BookingEmailOtpRequestResponse> requestOtp(
            @Valid @RequestBody BookingEmailOtpRequest request,
            HttpServletRequest http
    ) {
        return ApiResponse.ok(
                "Yêu cầu gửi OTP đã được tiếp nhận. Vui lòng kiểm tra email trong ít giây.",
                bookingEmailOtpService.requestOtp(request.getEmail(), getIp(http), http.getHeader("User-Agent"))
        );
    }

    @PostMapping("/verify")
    public ApiResponse<BookingEmailOtpVerifyResponse> verifyOtp(
            @Valid @RequestBody BookingEmailOtpVerifyRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Xác thực email thành công",
                bookingEmailOtpService.verifyOtp(
                        request.getVerificationId(),
                        request.getOtp(),
                        authenticatedUserId(authentication)
                )
        );
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

    private String getIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
