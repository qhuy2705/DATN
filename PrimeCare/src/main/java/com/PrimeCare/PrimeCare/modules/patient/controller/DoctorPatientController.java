package com.PrimeCare.PrimeCare.modules.patient.controller;

import com.PrimeCare.PrimeCare.modules.patient.dto.request.CreatePatientAllergyRequest;
import com.PrimeCare.PrimeCare.modules.patient.dto.response.MedicalTimelineEvent;
import com.PrimeCare.PrimeCare.modules.patient.dto.response.PatientAllergyResponse;
import com.PrimeCare.PrimeCare.modules.patient.dto.response.PatientResponse;
import com.PrimeCare.PrimeCare.modules.patient.service.MedicalTimelineService;
import com.PrimeCare.PrimeCare.modules.patient.service.PatientAllergyService;
import com.PrimeCare.PrimeCare.modules.patient.service.PatientService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/doctor/patients")
@RequiredArgsConstructor
public class DoctorPatientController {

    private final PatientService patientService;
    private final PatientAllergyService patientAllergyService;
    private final MedicalTimelineService medicalTimelineService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<PatientResponse> getPatient(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok("OK", patientService.getForDoctor(id, currentUserId(authentication)));
    }

    @GetMapping("/{id}/allergies")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<List<PatientAllergyResponse>> getAllergies(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok("OK", patientAllergyService.listForDoctor(id, currentUserId(authentication)));
    }

    @PostMapping("/{id}/allergies")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<PatientAllergyResponse> addAllergy(
            @PathVariable Long id,
            @Valid @RequestBody CreatePatientAllergyRequest request,
            Authentication authentication
    ) {
        Long doctorUserId = currentUserId(authentication);
        return ApiResponse.ok(
                "Đã ghi nhận dị ứng",
                patientAllergyService.createForDoctor(id, doctorUserId, request.getAllergenName(), request.getAllergyType(), request.getSeverity(), request.getReaction())
        );
    }

    @DeleteMapping("/allergies/{allergyId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<Void> deleteAllergy(
            @PathVariable Long allergyId,
            Authentication authentication
    ) {
        Long doctorUserId = currentUserId(authentication);
        patientAllergyService.deleteForDoctor(allergyId, doctorUserId);
        return ApiResponse.ok("Đã xóa dị ứng", null);
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<List<MedicalTimelineEvent>> getTimeline(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type,
            Authentication authentication
    ) {
        return ApiResponse.ok("OK", medicalTimelineService.getTimelineForDoctor(id, currentUserId(authentication), from, to, type));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
