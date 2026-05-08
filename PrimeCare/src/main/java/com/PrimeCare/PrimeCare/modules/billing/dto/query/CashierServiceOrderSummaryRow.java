package com.PrimeCare.PrimeCare.modules.billing.dto.query;

public interface CashierServiceOrderSummaryRow {
    long getServiceOrderCount();

    long getUninvoicedServiceOrderCount();

    long getUnpaidServiceOrderCount();
}
