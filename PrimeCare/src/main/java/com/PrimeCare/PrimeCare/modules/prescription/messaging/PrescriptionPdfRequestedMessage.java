package com.PrimeCare.PrimeCare.modules.prescription.messaging;

public record PrescriptionPdfRequestedMessage(
        Long jobId,
        Long prescriptionId
) {
}