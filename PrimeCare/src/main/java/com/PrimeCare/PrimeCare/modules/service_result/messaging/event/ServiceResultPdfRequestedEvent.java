package com.PrimeCare.PrimeCare.modules.service_result.messaging.event;

public record ServiceResultPdfRequestedEvent(
        Long serviceResultId,
        Long encounterId,
        Long serviceOrderItemId
) {
}
