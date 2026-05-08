package com.PrimeCare.PrimeCare.modules.encounter.dto.record;

public record EncounterWorkflowState(
        int serviceOrderCount,
        int pendingPaymentOrderCount,
        int activeServiceOrderCount,
        int waitingResultItemCount,
        int completedResultItemCount,
        int issuedPrescriptionCount,
        boolean hasPendingPayment,
        boolean hasWaitingResults,
        boolean readyForConclusion,
        boolean canCreatePrescription,
        boolean canComplete
) {
}
