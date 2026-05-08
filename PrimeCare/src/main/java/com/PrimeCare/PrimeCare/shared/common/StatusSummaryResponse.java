package com.PrimeCare.PrimeCare.shared.common;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StatusSummaryResponse {
    private long total;
    private long active;
    private long inactive;
}
