package com.PrimeCare.PrimeCare.modules.publiclookup.controller;

import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiSelectSlotRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.service.AiBookingAssistantService;
import com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/assistant")
@RequiredArgsConstructor
public class PublicAssistantController {

    // Official public assistant API namespace.
    private final PublicAssistantService publicAssistantService;
    private final AiBookingAssistantService aiBookingAssistantService;

    @PostMapping("/ask")
    public ApiResponse<PublicAssistantResponse> ask(@Valid @RequestBody PublicAssistantRequest request) {
        return ApiResponse.ok("OK", publicAssistantService.ask(request));
    }

    @PostMapping("/chat")
    public ApiResponse<PublicAssistantResponse> chat(@Valid @RequestBody PublicAssistantRequest request) {
        return ApiResponse.ok("OK", aiBookingAssistantService.chat(request));
    }

    @PostMapping("/select-slot")
    public ApiResponse<PublicAssistantResponse> selectSlot(@Valid @RequestBody AiSelectSlotRequest request) {
        return ApiResponse.ok("OK", aiBookingAssistantService.selectSlot(request));
    }
}
