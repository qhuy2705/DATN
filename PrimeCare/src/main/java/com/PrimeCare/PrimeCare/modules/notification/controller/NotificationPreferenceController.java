package com.PrimeCare.PrimeCare.modules.notification.controller;

import com.PrimeCare.PrimeCare.modules.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.PrimeCare.PrimeCare.modules.notification.dto.response.NotificationPreferenceResponse;
import com.PrimeCare.PrimeCare.modules.notification.service.NotificationPreferenceService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account/notification-preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    @GetMapping
    public ApiResponse<NotificationPreferenceResponse> get(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("OK", service.getForUser(userId));
    }

    @PutMapping
    public ApiResponse<NotificationPreferenceResponse> update(Authentication authentication,
                                                              @Valid @RequestBody UpdateNotificationPreferenceRequest req) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok("Updated", service.updateForUser(userId, req));
    }
}
