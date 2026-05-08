package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReceptionQueueSummaryResponse {
    private long total;
    private long requested;
    private long confirmed;
    private long checkedIn;
    private long arrived;
    private long notArrived;
    private long walkIn;
    private long priority;
    private long urgent;
    private long overdue;
    private long noShowFollowUpPending;
}
