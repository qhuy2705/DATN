package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
public class AvailabilityPreviewResponse {
    private List<DayPreview> days;

    @Data
    @Builder
    public static class DayPreview {
        private LocalDate visitDate;
        private List<SessionPreview> sessions;
    }

    @Data
    @Builder
    public static class SessionPreview {
        private BranchSessionType session;
        private int availableCount;
        private LocalTime firstAvailableSlot;
    }
}
