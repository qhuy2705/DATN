package com.PrimeCare.PrimeCare.modules.publiclookup.controller;

import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiSelectSlotRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.service.AiBookingAssistantService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Deprecated(since = "2026-05", forRemoval = false)
public class AiBookingController {

    private final AiBookingAssistantService aiBookingAssistantService;

    /*
     * Legacy compatibility namespace. New public clients should use
     * PublicAssistantController under /api/public/assistant.
     */
    @PostMapping("/chat")
    public ApiResponse<PublicAssistantResponse> chat(@Valid @RequestBody PublicAssistantRequest request) {
        return ApiResponse.ok("OK", aiBookingAssistantService.chat(request));
    }

    @PostMapping("/booking/select-slot")
    public ApiResponse<PublicAssistantResponse> selectSlot(@Valid @RequestBody AiSelectSlotRequest request) {
        return ApiResponse.ok("OK", aiBookingAssistantService.selectSlot(request));
    }
}
