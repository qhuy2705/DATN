package com.PrimeCare.PrimeCare.modules.appointment.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAppointmentIntakeRequest {

    @Size(max = 1000)
    private String reasonForVisit;

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

    @Min(value = 0)
    @Max(value = 300)
    private Double heightCm;

    @Min(value = 0)
    @Max(value = 1000)
    private Double weightKg;

    @Min(value = 30)
    @Max(value = 45)
    private Double temperatureC;

    @Min(value = 0)
    @Max(value = 300)
    private Integer pulse;

    @Min(value = 0)
    @Max(value = 400)
    private Integer systolicBp;

    @Min(value = 0)
    @Max(value = 300)
    private Integer diastolicBp;

    @Min(value = 0)
    @Max(value = 100)
    private Integer respiratoryRate;

    @Min(value = 0)
    @Max(value = 100)
    private Integer spo2;
}
