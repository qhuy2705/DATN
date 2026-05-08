package com.PrimeCare.PrimeCare.modules.patient.dto.request;

import com.PrimeCare.PrimeCare.modules.patient.entity.PatientAllergy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePatientAllergyRequest {
    @NotBlank(message = "Tên dị nguyên không được để trống")
    @Size(max = 255)
    private String allergenName;

    @NotNull(message = "Loại dị ứng không được để trống")
    private PatientAllergy.AllergyType allergyType;

    @NotNull(message = "Mức độ nghiêm trọng không được để trống")
    private PatientAllergy.AllergySeverity severity;

    private String reaction;
}
