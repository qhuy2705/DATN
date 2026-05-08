package com.PrimeCare.PrimeCare.modules.service_order.controller;

import com.PrimeCare.PrimeCare.modules.service_order.dto.request.CreateServiceOrderRequest;
import com.PrimeCare.PrimeCare.modules.service_order.dto.response.ServiceOrderResponse;
import com.PrimeCare.PrimeCare.modules.service_order.service.ServiceOrderService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctor/encounters/{encounterId}/service-orders")
@RequiredArgsConstructor
public class DoctorServiceOrderController {
    private final ServiceOrderService serviceOrderService;

    @GetMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<List<ServiceOrderResponse>> list(@PathVariable Long encounterId, Authentication authentication) {
        return ApiResponse.ok(
                "OK",
                serviceOrderService.listByEncounter(encounterId, Long.valueOf(authentication.getName()))
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiResponse<ServiceOrderResponse> create(
            @PathVariable Long encounterId,
            Authentication authentication,
            @Valid @RequestBody CreateServiceOrderRequest req
    ) {
        return ApiResponse.ok(
                "Tạo phiếu chỉ định thành công",
                serviceOrderService.create(encounterId, Long.valueOf(authentication.getName()), req)
        );
    }
}
