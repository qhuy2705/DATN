package com.PrimeCare.PrimeCare.modules.booking_restriction.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class BookingRestrictionAdminResponse {
    BookingRestrictionSummaryResponse restriction;
    List<PatientViolationEventResponse> events;
    List<BookingRestrictionOverrideResponse> overrides;
}
