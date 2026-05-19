package com.PrimeCare.PrimeCare.modules.appointment.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.FollowUpResolveRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAdminResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.FollowUpQueueItemResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAdminService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentFollowUpType;
import com.PrimeCare.PrimeCare.shared.enums.FollowUpQueueCategory;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/reception/follow-up")
@RequiredArgsConstructor
public class ReceptionFollowUpController {

    private final AppointmentAdminService appointmentAdminService;

    @GetMapping("/queue")
    @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<PageResponse<FollowUpQueueItemResponse>> queue(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String followUpType,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ApiResponse.ok(
                "OK",
                appointmentAdminService.getFollowUpQueue(
                        FollowUpQueueCategory.parse(category),
                        parseFollowUpType(followUpType),
                        q,
                        PaginationConfig.withoutSort(pageable)
                )
        );
    }

    @PostMapping("/{appointmentId}/resolve")
    @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<AppointmentAdminResponse> resolve(
            @PathVariable Long appointmentId,
            Authentication authentication,
            @RequestBody(required = false) FollowUpResolveRequest request
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        String note = request != null ? request.getNote() : null;

        return ApiResponse.ok(
                "Đã xử lý follow-up cho lịch hẹn",
                appointmentAdminService.resolveFollowUpAsClosed(appointmentId, currentUserId, note)
        );
    }

    private AppointmentFollowUpType parseFollowUpType(String value) {
        String normalized = StringUtil.trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return AppointmentFollowUpType.valueOf(normalized.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "Loại follow-up không hợp lệ."
            );
        }
    }
}
