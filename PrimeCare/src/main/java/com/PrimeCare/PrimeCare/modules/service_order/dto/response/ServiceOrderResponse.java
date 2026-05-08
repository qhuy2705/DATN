package com.PrimeCare.PrimeCare.modules.service_order.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ServiceOrderResponse {
    private Long id;
    private String code;
    private Long encounterId;
    private ServiceOrderStatus status;
    private PaymentStatus paymentStatus;
    private Long estimatedTotalAmount;
    private String note;
    private LocalDateTime orderedAt;
    private LocalDateTime paidAt;
    private List<ServiceOrderItemResponse> items;
}
