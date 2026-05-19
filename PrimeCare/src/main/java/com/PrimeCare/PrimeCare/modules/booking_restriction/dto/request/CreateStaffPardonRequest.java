package com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateStaffPardonRequest {
    private Long patientId;
    private Long appointmentId;
    private String phone;
    private String fullName;
    private LocalDate dob;
    private String email;

    @NotNull
    private Integer pointsToReduce;

    @NotBlank
    private String reason;
}
