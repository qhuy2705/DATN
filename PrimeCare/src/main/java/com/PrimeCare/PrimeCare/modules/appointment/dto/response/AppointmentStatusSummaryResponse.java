package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentStatusSummaryResponse {
    private long all;
    private long pending;
    private long confirmed;
    private long checkedIn;
    private long completed;
    private long noShow;
    private long cancelled;
}