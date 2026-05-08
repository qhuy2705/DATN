package com.PrimeCare.PrimeCare.modules.publiclookup.controller;

import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantResponse;
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

    private final PublicAssistantService publicAssistantService;

    @PostMapping("/ask")
    public ApiResponse<PublicAssistantResponse> ask(@Valid @RequestBody PublicAssistantRequest request) {
        return ApiResponse.ok("OK", publicAssistantService.ask(request));
    }
}
