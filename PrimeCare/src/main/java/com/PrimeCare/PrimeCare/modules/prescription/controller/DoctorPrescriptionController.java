package com.PrimeCare.PrimeCare.modules.prescription.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.prescription.dto.request.CreatePrescriptionRequest;
import com.PrimeCare.PrimeCare.modules.prescription.dto.request.UpdatePrescriptionRequest;
import com.PrimeCare.PrimeCare.modules.prescription.dto.response.PrescriptionResponse;
import com.PrimeCare.PrimeCare.modules.prescription.service.PrescriptionService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor/encounters/{encounterId}/prescriptions")
@RequiredArgsConstructor
public class DoctorPrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<PrescriptionResponse> create(
            @PathVariable Long encounterId,
            Authentication authentication,
            @Valid @RequestBody CreatePrescriptionRequest req
    ) {
        return ApiResponse.ok(
                "Tạo đơn thuốc thành công",
                prescriptionService.create(encounterId, Long.valueOf(authentication.getName()), req)
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<PageResponse<PrescriptionResponse>> list(
            @PathVariable Long encounterId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(
                "OK",
                prescriptionService.listByEncounter(
                        encounterId,
                        Long.valueOf(authentication.getName()),
                        PaginationConfig.withSort(pageable, Sort.by("createdAt").descending())
                )
        );
    }

    @PutMapping("/{prescriptionId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<PrescriptionResponse> update(
            @PathVariable Long encounterId,
            @PathVariable Long prescriptionId,
            Authentication authentication,
            @Valid @RequestBody UpdatePrescriptionRequest req
    ) {
        return ApiResponse.ok(
                "Cập nhật đơn thuốc thành công",
                prescriptionService.update(prescriptionId, Long.valueOf(authentication.getName()), req)
        );
    }

    @PostMapping("/{prescriptionId}/cancel")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<PrescriptionResponse> cancel(
            @PathVariable Long encounterId,
            @PathVariable Long prescriptionId,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Hủy đơn thuốc thành công",
                prescriptionService.cancel(prescriptionId, Long.valueOf(authentication.getName()))
        );
    }

    @GetMapping("/{prescriptionId}/pdf")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable Long encounterId,
            @PathVariable Long prescriptionId,
            Authentication authentication
    ) {
        byte[] pdf = prescriptionService.exportPdf(
                prescriptionId,
                Long.valueOf(authentication.getName())
        );

        String filename = "prescription-" + prescriptionId + ".pdf";

        return ResponseEntity.ok()
                             .contentType(MediaType.APPLICATION_PDF)
                             .header(
                                     HttpHeaders.CONTENT_DISPOSITION,
                                     ContentDisposition.inline().filename(filename).build().toString()
                             )
                             .body(pdf);
    }
}
