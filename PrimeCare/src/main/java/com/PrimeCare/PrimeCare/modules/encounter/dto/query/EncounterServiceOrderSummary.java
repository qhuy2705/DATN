package com.PrimeCare.PrimeCare.modules.encounter.dto.query;

public interface EncounterServiceOrderSummary {
    Long getEncounterId();

    long getServiceOrderCount();

    long getPendingPaymentOrderCount();

    long getActiveServiceOrderCount();

    long getWaitingResultItemCount();

    long getCompletedResultItemCount();
}
