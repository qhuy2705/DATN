package com.PrimeCare.PrimeCare.modules.booking_restriction.dto.response;

import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionOverrideStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class BookingRestrictionOverrideResponse {
    Long id;
    Long patientId;
    Long restrictionId;
    String identityKeyHash;
    BookingRestrictionOverrideStatus status;
    LocalDateTime expiresAt;
    String reason;
    Long createdById;
    LocalDateTime usedAt;
    Long usedAppointmentId;
    LocalDateTime createdAt;
}
