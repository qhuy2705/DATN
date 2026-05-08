package com.PrimeCare.PrimeCare.modules.appointment.dto.record;

public record CheckInTokenPayload(Long appointmentId, String code, String visitDate) {
}
