package com.PrimeCare.PrimeCare.modules.ratelimit.controller;

import com.PrimeCare.PrimeCare.modules.ratelimit.dto.request.CreateRateLimitRuleRequest;
import com.PrimeCare.PrimeCare.modules.ratelimit.dto.request.UpdateRateLimitRuleRequest;
import com.PrimeCare.PrimeCare.modules.ratelimit.dto.response.RateLimitRuleResponse;
import com.PrimeCare.PrimeCare.modules.ratelimit.service.RateLimitRuleService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/rate-limit-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminRateLimitRuleController {

    private final RateLimitRuleService rateLimitRuleService;

    @GetMapping
    public ApiResponse<List<RateLimitRuleResponse>> list() {
        return ApiResponse.ok("OK", rateLimitRuleService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<RateLimitRuleResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok("OK", rateLimitRuleService.detail(id));
    }

    @PostMapping
    public ApiResponse<RateLimitRuleResponse> create(
            @RequestBody CreateRateLimitRuleRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Đã tạo rate limit rule",
                rateLimitRuleService.create(request, currentUserId(authentication))
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<RateLimitRuleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRateLimitRuleRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Đã cập nhật rate limit rule",
                rateLimitRuleService.update(id, request, currentUserId(authentication))
        );
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<RateLimitRuleResponse> enable(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Đã bật rate limit rule",
                rateLimitRuleService.enable(id, currentUserId(authentication))
        );
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<RateLimitRuleResponse> disable(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Đã tắt rate limit rule",
                rateLimitRuleService.disable(id, currentUserId(authentication))
        );
    }

    @PostMapping("/{id}/reset-defaults")
    public ApiResponse<RateLimitRuleResponse> resetDefaults(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Đã khôi phục mặc định rate limit rule",
                rateLimitRuleService.resetDefaults(id, currentUserId(authentication))
        );
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
