package com.PrimeCare.PrimeCare.modules.booking_restriction.dto.response;

import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventSource;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.ViolationEventType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class PatientViolationEventResponse {
    Long id;
    Long patientId;
    Long appointmentId;
    String identityKeyHash;
    String periodMonth;
    ViolationEventType type;
    ViolationEventSource source;
    ViolationEventStatus status;
    int points;
    String note;
    Long createdById;
    LocalDateTime createdAt;
    Long voidedById;
    LocalDateTime voidedAt;
    String voidReason;
}
