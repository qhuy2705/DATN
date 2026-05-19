package com.PrimeCare.PrimeCare.modules.publiclookup.dto.response;

import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiAvailableSlotResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiBookingDraftResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiConversationContext;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiSuggestedDoctorResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PublicAssistantResponse {
    private String answer;
    private String conversationId;
    private String message;
    private String intent;
    private String caution;
    private String provider;
    private AiConversationContext context;
    private AiSuggestedDoctorResponse suggestedDoctor;
    private List<AiAvailableSlotResponse> availableSlots;
    private List<PublicAssistantActionResponse> actions;
    private AiBookingDraftResponse bookingDraft;
    private Boolean clarificationNeeded;
    private List<String> suggestions;
}
