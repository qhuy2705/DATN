package com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationContext {
    private String conversationId;
    private String lastIntent;
    private Long currentSpecialtyId;
    private String currentSpecialtyName;
    private Long currentDoctorId;
    private String currentDoctorName;
    private Long currentFacilityId;
    private String currentFacilityName;
    private String currentFacilityAddress;
    private List<AiSuggestedDoctorResponse> lastSuggestedDoctors;
    private List<AiAvailableSlotResponse> lastAvailableSlots;
    private LocalDate lastShownDate;
    private LocalDate preferredDate;
    private LocalDate preferredFromDate;
    private String preferredWeekday;
    private String preferredAfterTime;
    private String preferredBeforeTime;
    private String userTimePreference;
    private String userSessionType;
    private AiBookingDraftResponse pendingBookingDraft;
    private String lastAiAction;
    private Boolean clarificationNeeded;
}
