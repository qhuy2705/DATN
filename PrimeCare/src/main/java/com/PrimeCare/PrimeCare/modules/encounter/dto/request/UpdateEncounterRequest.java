package com.PrimeCare.PrimeCare.modules.encounter.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateEncounterRequest {

    @Size(max = 2000)
    private String chiefComplaint;

    @Size(max = 1000)
    private String intakeReasonForVisit;

    @Size(max = 32)
    private String visitType;

    @Size(max = 32)
    private String triagePriority;

    @Size(max = 2000)
    private String triageNote;

    @Size(max = 500)
    private String insuranceNote;

    @Size(max = 255)
    private String emergencyContactName;

    @Size(max = 32)
    private String emergencyContactPhone;

    @Size(max = 4000)
    private String clinicalNote;

    @Size(max = 4000)
    private String preliminaryDiagnosis;

    @Size(max = 4000)
    private String finalDiagnosis;

    @Size(max = 4000)
    private String conclusion;

    @Min(value = 0, message = "Chiều cao phải lớn hơn hoặc bằng 0")
    @Max(value = 300, message = "Chiều cao không hợp lệ")
    private Double heightCm;

    @Min(value = 0, message = "Cân nặng phải lớn hơn hoặc bằng 0")
    @Max(value = 1000, message = "Cân nặng không hợp lệ")
    private Double weightKg;

    @Min(value = 30, message = "Nhiệt độ không hợp lệ")
    @Max(value = 45, message = "Nhiệt độ không hợp lệ")
    private Double temperatureC;

    @Min(value = 0, message = "Mạch không hợp lệ")
    @Max(value = 300, message = "Mạch không hợp lệ")
    private Integer pulse;

    @Min(value = 0, message = "Huyết áp tâm thu không hợp lệ")
    @Max(value = 400, message = "Huyết áp tâm thu không hợp lệ")
    private Integer systolicBp;

    @Min(value = 0, message = "Huyết áp tâm trương không hợp lệ")
    @Max(value = 300, message = "Huyết áp tâm trương không hợp lệ")
    private Integer diastolicBp;

    @Min(value = 0, message = "Nhịp thở không hợp lệ")
    @Max(value = 100, message = "Nhịp thở không hợp lệ")
    private Integer respiratoryRate;

    @Min(value = 0, message = "SpO2 không hợp lệ")
    @Max(value = 100, message = "SpO2 không hợp lệ")
    private Integer spo2;

    @Size(max = 2000)
    private String allergySnapshot;

    @Size(max = 2000)
    private String chronicDiseaseSnapshot;

    @Size(max = 4000)
    private String pastMedicalHistory;

    @Size(max = 4000)
    private String physicalExamination;

    @Size(max = 4000)
    private String treatmentPlan;

    private LocalDate followUpDate;

    @Size(max = 2000)
    private String followUpNote;
}