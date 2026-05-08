package com.PrimeCare.PrimeCare.modules.publiclookup.controller;

import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAppointmentCancelRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicLookupOtpRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicLookupVerifyRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.AppointmentLookupVerifyResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAppointmentActionResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicLookupOtpResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.ResultLookupVerifyResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAppointmentLookupService;
import com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicResultLookupService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/lookup")
@RequiredArgsConstructor
public class PublicLookupController {
    private final PublicAppointmentLookupService appointmentLookupService;
    private final PublicResultLookupService resultLookupService;

    @PostMapping("/appointments/request-otp")
    public ApiResponse<PublicLookupOtpResponse> requestAppointmentOtp(@Valid @RequestBody PublicLookupOtpRequest request) {
        return ApiResponse.ok("Yêu cầu gửi OTP đã được tiếp nhận. Vui lòng kiểm tra email trong ít giây.", appointmentLookupService.requestOtp(request.getCode()));
    }

    @PostMapping("/appointments/verify-otp")
    public ApiResponse<AppointmentLookupVerifyResponse> verifyAppointmentOtp(@Valid @RequestBody PublicLookupVerifyRequest request) {
        return ApiResponse.ok("Xác thực OTP thành công", appointmentLookupService.verifyOtp(request.getCode(), request.getOtp()));
    }

    @PostMapping("/appointments/{code}/cancel")
    public ApiResponse<PublicAppointmentActionResponse> cancelAppointment(
            @PathVariable String code,
            @RequestParam(required = false) String token,
            @RequestHeader(value = "X-Lookup-Token", required = false) String headerToken,
            @Valid @RequestBody PublicAppointmentCancelRequest request
    ) {
        return ApiResponse.ok("Huy lich thanh cong", appointmentLookupService.cancel(code, resolveToken(token, headerToken), request.getReason()));
    }

    @GetMapping("/appointments/{code}/pdf")
    public ResponseEntity<byte[]> downloadAppointmentPdf(
            @PathVariable String code,
            @RequestParam(required = false) String token,
            @RequestHeader(value = "X-Lookup-Token", required = false) String headerToken
    ) {
        byte[] pdf = appointmentLookupService.downloadPdf(code, resolveToken(token, headerToken));
        return ResponseEntity.ok()
                             .contentType(MediaType.APPLICATION_PDF)
                             .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("appointment-" + code + ".pdf").build().toString())
                             .body(pdf);
    }

    @PostMapping("/results/request-otp")
    public ApiResponse<PublicLookupOtpResponse> requestResultOtp(@Valid @RequestBody PublicLookupOtpRequest request) {
        return ApiResponse.ok("Yêu cầu gửi OTP đã được tiếp nhận. Vui lòng kiểm tra email trong ít giây.", resultLookupService.requestOtp(request.getCode()));
    }

    @PostMapping("/results/verify-otp")
    public ApiResponse<ResultLookupVerifyResponse> verifyResultOtp(@Valid @RequestBody PublicLookupVerifyRequest request) {
        return ApiResponse.ok("Xác thực OTP thành công", resultLookupService.verifyOtp(request.getCode(), request.getOtp()));
    }

    @GetMapping("/results/{code}/pdf")
    public ResponseEntity<byte[]> downloadResultPdf(
            @PathVariable String code,
            @RequestParam(required = false) String token,
            @RequestHeader(value = "X-Lookup-Token", required = false) String headerToken
    ) {
        byte[] pdf = resultLookupService.downloadPdf(code, resolveToken(token, headerToken));
        return ResponseEntity.ok()
                             .contentType(MediaType.APPLICATION_PDF)
                             .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("medical-result-" + code + ".pdf").build().toString())
                             .body(pdf);
    }

    private String resolveToken(String queryToken, String headerToken) {
        String rawToken = headerToken != null && !headerToken.isBlank() ? headerToken : queryToken;
        String token = rawToken != null ? rawToken.trim() : null;
        if (token == null || token.isBlank()) {
            throw new ApiException(ErrorCode.PUBLIC_LOOKUP_TOKEN_INVALID);
        }
        return token;
    }
}
