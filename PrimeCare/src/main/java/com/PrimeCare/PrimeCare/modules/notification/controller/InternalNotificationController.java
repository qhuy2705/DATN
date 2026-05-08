package com.PrimeCare.PrimeCare.modules.notification.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.notification.dto.response.InternalNotificationResponse;
import com.PrimeCare.PrimeCare.modules.notification.dto.response.InternalNotificationUnreadCountResponse;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final InternalNotificationService internalNotificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<InternalNotificationResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String severity,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(
                "OK",
                internalNotificationService.list(
                        Long.valueOf(authentication.getName()),
                        unreadOnly,
                        type,
                        severity,
                        PaginationConfig.withSort(pageable, Sort.by("createdAt").descending().and(Sort.by("id").descending()))
                )
        );
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<InternalNotificationUnreadCountResponse> unreadCount(Authentication authentication) {
        return ApiResponse.ok(
                "OK",
                internalNotificationService.unreadCount(Long.valueOf(authentication.getName()))
        );
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<InternalNotificationResponse> markRead(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Đã đánh dấu đã đọc",
                internalNotificationService.markRead(id, Long.valueOf(authentication.getName()))
        );
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<InternalNotificationUnreadCountResponse> markAllRead(Authentication authentication) {
        return ApiResponse.ok(
                "Đã đánh dấu tất cả là đã đọc",
                internalNotificationService.markAllRead(Long.valueOf(authentication.getName()))
        );
    }
}
