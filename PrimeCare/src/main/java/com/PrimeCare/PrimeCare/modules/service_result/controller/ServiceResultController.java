package com.PrimeCare.PrimeCare.modules.service_result.controller;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.audit.dto.response.AuditLogResponse;
import com.PrimeCare.PrimeCare.modules.service_result.dto.request.SubmitServiceResultRequest;
import com.PrimeCare.PrimeCare.modules.service_result.dto.response.ServiceDeskQueueItemResponse;
import com.PrimeCare.PrimeCare.modules.service_result.dto.response.ServiceDeskSummaryResponse;
import com.PrimeCare.PrimeCare.modules.service_result.dto.response.ServiceResultResponse;
import com.PrimeCare.PrimeCare.modules.service_result.service.ServiceResultService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/service-desk/results")
@RequiredArgsConstructor
public class ServiceResultController {

    private final ServiceResultService serviceResultService;

    @GetMapping
    @PreAuthorize("hasRole('SERVICE_TECHNICIAN')")
    public ApiResponse<PageResponse<ServiceDeskQueueItemResponse>> search(
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) ServiceOrderItemStatus itemStatus,
            @RequestParam(required = false) ServiceResultStatus resultStatus,
            @RequestParam(required = false) String q,
            Pageable pageable
    ) {
        return ApiResponse.ok(
                serviceResultService.searchQueue(
                        departmentCode,
                        itemStatus,
                        resultStatus,
                        q,
                        PaginationConfig.withoutSort(pageable)
                )
        );
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('SERVICE_TECHNICIAN')")
    public ApiResponse<ServiceDeskSummaryResponse> summary(
            @RequestParam(required = false) String departmentCode
    ) {
        return ApiResponse.ok(serviceResultService.summary(departmentCode));
    }

    @GetMapping("/{itemId}/history")
    @PreAuthorize("hasRole('SERVICE_TECHNICIAN')")
    public ApiResponse<java.util.List<AuditLogResponse>> history(@PathVariable Long itemId) {
        return ApiResponse.ok("OK", serviceResultService.history(itemId));
    }

    @PostMapping("/{itemId}")
    @PreAuthorize("hasRole('SERVICE_TECHNICIAN')")
    public ApiResponse<ServiceResultResponse> submit(
            @PathVariable Long itemId,
            Authentication authentication,
            @Valid @RequestBody SubmitServiceResultRequest req
    ) {
        return ApiResponse.ok(
                "Cập nhật kết quả thành công",
                serviceResultService.submit(itemId, Long.valueOf(authentication.getName()), req)
        );
    }

    @PostMapping("/{itemId}/verify")
    @PreAuthorize("hasRole('SERVICE_TECHNICIAN')")
    public ApiResponse<ServiceResultResponse> verify(
            @PathVariable Long itemId,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                "Xác nhận kết quả thành công",
                serviceResultService.verify(itemId, Long.valueOf(authentication.getName()))
        );
    }

    @GetMapping("/{itemId}/pdf")
    @PreAuthorize("hasRole('SERVICE_TECHNICIAN')")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable Long itemId,
            Authentication authentication
    ) {
        byte[] pdf = serviceResultService.downloadReportPdf(itemId, Long.valueOf(authentication.getName()));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename("service-result-" + itemId + ".pdf")
                                .build()
                                .toString()
                )
                .body(pdf);
    }
}
