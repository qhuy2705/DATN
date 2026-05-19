package com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request;

import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateViolationEventRequest {
    private Long patientId;
    private Long appointmentId;
    private String phone;
    private String fullName;
    private LocalDate dob;
    private String email;
    private ViolationEventType type;

    @NotNull
    private Integer points;

    @NotBlank
    private String reason;
}
