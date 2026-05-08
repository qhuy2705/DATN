package com.PrimeCare.PrimeCare.modules.prescription.controller;

import com.PrimeCare.PrimeCare.modules.prescription.dto.response.PrescriptionPdfJobResponse;
import com.PrimeCare.PrimeCare.modules.prescription.entity.PrescriptionPdfJob;
import com.PrimeCare.PrimeCare.modules.prescription.service.PrescriptionPdfJobService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionPdfJobStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor/prescription-pdf-jobs")
@RequiredArgsConstructor
public class DoctorPrescriptionPdfController {

    private final PrescriptionPdfJobService jobService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<PrescriptionPdfJobResponse> requestGenerate(
            @RequestParam Long prescriptionId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Long doctorUserId = Long.valueOf(authentication.getName());
        PrescriptionPdfJob job = jobService.requestGenerate(prescriptionId, doctorUserId);

        return ApiResponse.ok("Da tao job sinh PDF", toResponse(job, request));
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<PrescriptionPdfJobResponse> getJob(
            @PathVariable Long jobId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Long doctorUserId = Long.valueOf(authentication.getName());
        PrescriptionPdfJob job = jobService.getJob(jobId, doctorUserId);

        return ApiResponse.ok("OK", toResponse(job, request));
    }

    @GetMapping("/{jobId}/download")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<byte[]> download(
            @PathVariable Long jobId,
            Authentication authentication
    ) {
        Long doctorUserId = Long.valueOf(authentication.getName());
        byte[] pdf = jobService.download(jobId, doctorUserId);
        PrescriptionPdfJob job = jobService.getJob(jobId, doctorUserId);

        return ResponseEntity.ok()
                             .contentType(MediaType.APPLICATION_PDF)
                             .header(
                                     HttpHeaders.CONTENT_DISPOSITION,
                                     ContentDisposition.inline()
                                                       .filename("prescription-" + job.getPrescriptionId() + ".pdf")
                                                       .build()
                                                       .toString()
                             )
                             .body(pdf);
    }

    private PrescriptionPdfJobResponse toResponse(PrescriptionPdfJob job, HttpServletRequest request) {
        String downloadUrl = job.getStatus() == PrescriptionPdfJobStatus.COMPLETED
                ? request.getRequestURL().toString().replace(request.getRequestURI(), "")
                + "/api/doctor/prescription-pdf-jobs/" + job.getId() + "/download"
                : null;

        return PrescriptionPdfJobResponse.builder()
                                         .id(job.getId())
                                         .prescriptionId(job.getPrescriptionId())
                                         .status(job.getStatus())
                                         .errorMessage(job.getErrorMessage())
                                         .downloadUrl(downloadUrl)
                                         .build();
    }
}