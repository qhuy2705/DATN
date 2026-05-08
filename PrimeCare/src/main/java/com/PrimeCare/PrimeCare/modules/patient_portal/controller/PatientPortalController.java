package com.PrimeCare.PrimeCare.modules.patient_portal.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.patient.dto.response.PatientResponse;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.request.CancelPatientAppointmentRequest;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.request.UpdatePatientSelfProfileRequest;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.response.PatientAppointmentHistoryItemResponse;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.response.PatientInvoiceHistoryItemResponse;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.response.PatientOverviewResponse;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.response.PatientResultHistoryItemResponse;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.response.PatientStatusHistoryItemResponse;
import com.PrimeCare.PrimeCare.modules.patient_portal.service.PatientPortalService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientPortalController {

    private final PatientPortalService service;

    @GetMapping("/overview")
    public ApiResponse<PatientOverviewResponse> overview(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("OK", service.getOverview(userId));
    }

    @GetMapping("/me")
    public ApiResponse<PatientResponse> getMyProfile(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("OK", service.getMyProfile(userId));
    }

    @PutMapping("/me")
    public ApiResponse<PatientResponse> updateMyProfile(Authentication authentication,
                                                        @Valid @RequestBody UpdatePatientSelfProfileRequest req) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("Updated", service.updateMyProfile(userId, req));
    }

    @GetMapping("/appointments")
    public ApiResponse<PageResponse<PatientAppointmentHistoryItemResponse>> appointments(
            Authentication authentication,
            @PageableDefault(size = 10, sort = {"visitDate", "createdAt"}, direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "OK",
                service.listMyAppointments(
                        userId,
                        PaginationConfig.withSort(
                                pageable,
                                Sort.by("visitDate").descending().and(Sort.by("createdAt").descending())
                        )
                )
        );
    }

    @GetMapping("/appointments/{appointmentId}/status-history")
    public ApiResponse<List<PatientStatusHistoryItemResponse>> appointmentStatusHistory(
            Authentication authentication,
            @PathVariable Long appointmentId
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("OK", service.getAppointmentStatusHistory(userId, appointmentId));
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    public ApiResponse<PatientAppointmentHistoryItemResponse> cancelMyAppointment(
            Authentication authentication,
            @PathVariable Long appointmentId,
            @RequestBody(required = false) CancelPatientAppointmentRequest req
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("Đã hủy lịch hẹn", service.cancelMyAppointment(userId, appointmentId, req != null ? req.getReason() : null));
    }

    @GetMapping("/results")
    public ApiResponse<PageResponse<PatientResultHistoryItemResponse>> results(
            Authentication authentication,
            @PageableDefault(size = 10, sort = {"verifiedAt", "createdAt"}, direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "OK",
                service.listMyResults(
                        userId,
                        PaginationConfig.withSort(
                                pageable,
                                Sort.by("verifiedAt").descending().and(Sort.by("createdAt").descending())
                        )
                )
        );
    }

    @GetMapping("/results/{resultId}/status-history")
    public ApiResponse<List<PatientStatusHistoryItemResponse>> resultStatusHistory(
            Authentication authentication,
            @PathVariable Long resultId
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("OK", service.getResultStatusHistory(userId, resultId));
    }

    @GetMapping("/results/{resultId}/pdf")
    public ResponseEntity<byte[]> downloadResultPdf(
            Authentication authentication,
            @PathVariable Long resultId
    ) {
        Long userId = Long.valueOf(authentication.getName());
        byte[] pdf = service.downloadMyResultPdf(userId, resultId);
        String fileName = service.buildResultPdfFileName(userId, resultId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(fileName).build().toString())
                .body(pdf);
    }

    @GetMapping("/invoices")
    public ApiResponse<PageResponse<PatientInvoiceHistoryItemResponse>> invoices(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(
                "OK",
                service.listMyInvoices(userId, PaginationConfig.withSort(pageable, Sort.by("createdAt").descending()))
        );
    }

    @GetMapping("/invoices/{invoiceId}/status-history")
    public ApiResponse<List<PatientStatusHistoryItemResponse>> invoiceStatusHistory(
            Authentication authentication,
            @PathVariable Long invoiceId
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("OK", service.getInvoiceStatusHistory(userId, invoiceId));
    }

    @GetMapping("/invoices/{invoiceId}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(
            Authentication authentication,
            @PathVariable Long invoiceId
    ) {
        Long userId = Long.valueOf(authentication.getName());
        byte[] pdf = service.downloadMyInvoicePdf(userId, invoiceId);
        String fileName = service.buildInvoicePdfFileName(userId, invoiceId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(pdf);
    }
}
