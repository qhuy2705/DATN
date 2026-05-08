package com.PrimeCare.PrimeCare.modules.encounter.controller;

import com.PrimeCare.PrimeCare.modules.encounter.dto.request.SaveEncounterDiagnosesRequest;
import com.PrimeCare.PrimeCare.modules.encounter.dto.request.UpdateEncounterRequest;
import com.PrimeCare.PrimeCare.modules.encounter.dto.response.EncounterAiSummaryResponse;
import com.PrimeCare.PrimeCare.modules.encounter.dto.response.EncounterDiagnosisResponse;
import com.PrimeCare.PrimeCare.modules.encounter.dto.response.EncounterResponse;
import com.PrimeCare.PrimeCare.modules.encounter.dto.response.EncounterTimelineItemResponse;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterAiSummaryService;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterDiagnosisService;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctor/encounters")
@RequiredArgsConstructor
public class DoctorEncounterController {

    private final EncounterService encounterService;
    private final EncounterAiSummaryService encounterAiSummaryService;
    private final EncounterDiagnosisService encounterDiagnosisService;

    @PostMapping("/from-appointment/{appointmentId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<EncounterResponse> create(@PathVariable Long appointmentId, Authentication authentication) {
        return ApiResponse.ok(
                "Tạo lần khám thành công",
                encounterService.createFromAppointment(appointmentId, Long.valueOf(authentication.getName()))
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<EncounterResponse> get(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok("OK", encounterService.get(id, Long.valueOf(authentication.getName())));
    }


    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<java.util.List<EncounterTimelineItemResponse>> timeline(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok("OK", encounterService.getTimeline(id, Long.valueOf(authentication.getName())));
    }

    @GetMapping("/{id}/ai-summary")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<EncounterAiSummaryResponse> aiSummary(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(
                "OK",
                encounterAiSummaryService.generate(id, Long.valueOf(authentication.getName()))
        );
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<EncounterResponse> update(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody UpdateEncounterRequest req
    ) {
        return ApiResponse.ok(
                "Cập nhật hồ sơ khám thành công",
                encounterService.update(id, Long.valueOf(authentication.getName()), req)
        );
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<EncounterResponse> complete(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody UpdateEncounterRequest req
    ) {
        return ApiResponse.ok(
                "Hoàn tất lần khám",
                encounterService.complete(id, Long.valueOf(authentication.getName()), req)
        );
    }

    // ==================== ICD-10 Diagnosis Endpoints ====================

    @GetMapping("/{id}/diagnoses")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<List<EncounterDiagnosisResponse>> getDiagnoses(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "OK",
                encounterDiagnosisService.getDiagnoses(id, Long.valueOf(authentication.getName()))
        );
    }

    @PostMapping("/{id}/diagnoses")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<List<EncounterDiagnosisResponse>> saveDiagnoses(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody SaveEncounterDiagnosesRequest request
    ) {
        return ApiResponse.ok(
                "Lưu chẩn đoán thành công",
                encounterDiagnosisService.saveDiagnoses(id, Long.valueOf(authentication.getName()), request)
        );
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('DOCTOR', 'OPERATIONS_ADMIN')")
    public ApiResponse<EncounterResponse> reopenEncounter(
            @PathVariable Long id,
            @RequestParam String reason,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Đã mở lại lần khám",
                encounterService.reopenEncounter(id, Long.valueOf(authentication.getName()), reason)
        );
    }

    @PostMapping("/{id}/validate-vitals")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<java.util.List<com.PrimeCare.PrimeCare.modules.encounter.validation.VitalSignValidator.VitalSignWarning>> validateVitals(
            @PathVariable Long id,
            @RequestBody UpdateEncounterRequest req
    ) {
        var warnings = com.PrimeCare.PrimeCare.modules.encounter.validation.VitalSignValidator.validate(
                req.getHeightCm(), req.getWeightKg(), req.getTemperatureC(),
                req.getPulse(), req.getSystolicBp(), req.getDiastolicBp(),
                req.getRespiratoryRate(), req.getSpo2()
        );
        return ApiResponse.ok("OK", warnings);
    }
}
