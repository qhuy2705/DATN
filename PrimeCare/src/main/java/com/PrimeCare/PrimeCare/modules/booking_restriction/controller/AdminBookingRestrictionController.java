package com.PrimeCare.PrimeCare.modules.booking_restriction.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request.CreateBookingRestrictionOverrideRequest;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request.CreateStaffPardonRequest;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request.CreateViolationEventRequest;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request.LiftBookingRestrictionRequest;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request.VoidViolationEventRequest;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.response.BookingRestrictionAdminResponse;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.response.BookingRestrictionOverrideResponse;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.response.BookingRestrictionSummaryResponse;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.response.PatientViolationEventResponse;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionLevel;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.BookingRestrictionAdminService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/booking-restrictions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATIONS_ADMIN')")
public class AdminBookingRestrictionController {

    private final BookingRestrictionAdminService bookingRestrictionAdminService;

    @GetMapping
    public ApiResponse<PageResponse<BookingRestrictionSummaryResponse>> list(
            @RequestParam(required = false) BookingRestrictionStatus status,
            @RequestParam(required = false) BookingRestrictionLevel level,
            @RequestParam(required = false) String periodMonth,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(
                "OK",
                bookingRestrictionAdminService.list(
                        status,
                        level,
                        periodMonth,
                        q,
                        PaginationConfig.withSort(pageable, Sort.by(Sort.Direction.DESC, "createdAt"))
                )
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingRestrictionAdminResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok("OK", bookingRestrictionAdminService.detail(id));
    }

    @PostMapping("/{id}/lift")
    public ApiResponse<BookingRestrictionAdminResponse> lift(
            @PathVariable Long id,
            @Valid @RequestBody LiftBookingRestrictionRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Đã gỡ hạn chế đặt lịch",
                bookingRestrictionAdminService.lift(id, request, currentUserId(authentication))
        );
    }

    @PostMapping("/{id}/override-once")
    public ApiResponse<BookingRestrictionOverrideResponse> overrideOnce(
            @PathVariable Long id,
            @Valid @RequestBody CreateBookingRestrictionOverrideRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Đã tạo override đặt lịch một lần",
                bookingRestrictionAdminService.overrideOnce(id, request, currentUserId(authentication))
        );
    }

    @PostMapping("/violations")
    public ApiResponse<PatientViolationEventResponse> createViolation(
            @Valid @RequestBody CreateViolationEventRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Đã tạo sự kiện điều chỉnh điểm",
                bookingRestrictionAdminService.createViolation(request, currentUserId(authentication))
        );
    }

    @PostMapping("/violations/{eventId}/void")
    public ApiResponse<PatientViolationEventResponse> voidViolation(
            @PathVariable Long eventId,
            @Valid @RequestBody VoidViolationEventRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Đã void sự kiện vi phạm",
                bookingRestrictionAdminService.voidViolation(eventId, request, currentUserId(authentication))
        );
    }

    @PostMapping("/pardon")
    public ApiResponse<PatientViolationEventResponse> pardon(
            @Valid @RequestBody CreateStaffPardonRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Đã ân xá điểm vi phạm",
                bookingRestrictionAdminService.pardon(request, currentUserId(authentication))
        );
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
