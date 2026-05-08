package com.PrimeCare.PrimeCare.modules.publiccontact.controller;

import com.PrimeCare.PrimeCare.modules.publiccontact.dto.request.UpdatePublicContactStatusRequest;
import com.PrimeCare.PrimeCare.modules.publiccontact.dto.response.AdminPublicContactSubmissionResponse;
import com.PrimeCare.PrimeCare.modules.publiccontact.service.PublicContactService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/public-contact/submissions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN')")
public class AdminPublicContactController {

    private final PublicContactService publicContactService;

    @GetMapping
    public ApiResponse<PageResponse<AdminPublicContactSubmissionResponse>> list(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok("OK", publicContactService.listSubmissions(status, pageable));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminPublicContactSubmissionResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePublicContactStatusRequest request
    ) {
        return ApiResponse.ok("Đã cập nhật trạng thái yêu cầu liên hệ", publicContactService.updateStatus(id, request));
    }
}
