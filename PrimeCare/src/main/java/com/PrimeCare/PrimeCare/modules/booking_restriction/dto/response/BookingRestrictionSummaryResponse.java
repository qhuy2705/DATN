package com.PrimeCare.PrimeCare.modules.booking_restriction.dto.response;

import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionLevel;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class BookingRestrictionSummaryResponse {
    Long id;
    Long patientId;
    String patientFullName;
    String patientPhone;
    String patientEmail;
    String identityKeyHash;
    String periodMonth;
    BookingRestrictionLevel level;
    BookingRestrictionStatus status;
    int scoreSnapshot;
    int currentScore;
    String reason;
    LocalDateTime startsAt;
    LocalDateTime expiresAt;
    Long createdById;
    Long liftedById;
    LocalDateTime liftedAt;
    String liftReason;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Boolean canLift;
    Boolean canOverrideOnce;
    Boolean canStaffPardon;
    Boolean canCreateManualViolation;
    List<String> supportedActions;
}
