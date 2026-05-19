package com.PrimeCare.PrimeCare.modules.billing.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RevenueReportResponse {
    private Long totalRevenue;
    private Long totalRefunded;
    private Long netRevenue;
    private Long refundsProcessed;
    private Long totalInvoices;
    private Long paidInvoices;
    private Long refundedInvoices;
    private List<RevenueByPeriod> byPeriod;
    private List<RevenueByService> byService;

    @Data
    @Builder
    public static class RevenueByPeriod {
        private String period; // "2024-05-01" or "2024-W20" or "2024-05"
        private Long revenue;
        private Long invoiceCount;
    }

    @Data
    @Builder
    public static class RevenueByService {
        private String serviceName;
        private Long revenue;
        private Long quantity;
    }
}
