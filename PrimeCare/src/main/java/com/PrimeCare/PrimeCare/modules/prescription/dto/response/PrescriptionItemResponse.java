package com.PrimeCare.PrimeCare.modules.prescription.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.PrescriptionItemStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PrescriptionItemResponse {
    private Long id;
    private Long medicationId;
    private String medicationCode;
    private String medicationName;
    private String strength;
    private String dosageForm;
    private String unit;
    private Integer quantity;
    private String dose;
    private String frequency;
    private Integer durationDays;
    private String route;
    private String instruction;
    private PrescriptionItemStatus status;
    private String refundReason;
    private LocalDateTime refundedAt;
}
