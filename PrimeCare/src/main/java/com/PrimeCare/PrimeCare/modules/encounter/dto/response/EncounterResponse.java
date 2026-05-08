package com.PrimeCare.PrimeCare.modules.encounter.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EncounterResponse {

    private Long id;
    private String code;

    private Long appointmentId;
    private Long patientId;

    private Long doctorId;
    private String doctorName;

    private Long branchId;
    private String branchNameVn;
    private String branchNameEn;

    private Long specialtyId;
    private String specialtyNameVn;
    private String specialtyNameEn;

    private String patientFullName;
    private String patientPhone;
    private String patientEmail;
    private LocalDate patientDob;
    private String patientGender;

    private String intakeReasonForVisit;
    private String visitType;
    // Legacy DB names are kept for compatibility; reception* exposes current product language.
    private String triagePriority;
    private String triageNote;
    private String receptionPriority;
    private String receptionNote;
    private String insuranceNote;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private LocalDateTime intakeCompletedAt;
    private String intakeCompletedByName;

    private String chiefComplaint;
    private String clinicalNote;
    private String preliminaryDiagnosis;
    private String finalDiagnosis;
    private String conclusion;

    // ICD-10 structured diagnoses
    private List<EncounterDiagnosisResponse> diagnoses;

    private Double heightCm;
    private Double weightKg;
    private Double temperatureC;
    private Integer pulse;
    private Integer systolicBp;
    private Integer diastolicBp;
    private Integer respiratoryRate;
    private Integer spo2;

    private String allergySnapshot;
    private String chronicDiseaseSnapshot;
    private String pastMedicalHistory;
    private String physicalExamination;
    private String treatmentPlan;
    private LocalDate followUpDate;
    private String followUpNote;

    private EncounterStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Integer serviceOrderCount;
    private Integer pendingPaymentOrderCount;
    private Integer activeServiceOrderCount;
    private Integer waitingResultItemCount;
    private Integer completedResultItemCount;
    private Integer issuedPrescriptionCount;
    private Boolean hasPendingPayment;
    private Boolean hasWaitingResults;
    private Boolean readyForConclusion;
    private Boolean canCreatePrescription;
    private Boolean canComplete;
}
