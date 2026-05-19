package com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiSelectSlotRequest {
    private String conversationId;

    @Valid
    private AiConversationContext context;

    @NotBlank(message = "slotId khong duoc de trong")
    private String slotId;
}
