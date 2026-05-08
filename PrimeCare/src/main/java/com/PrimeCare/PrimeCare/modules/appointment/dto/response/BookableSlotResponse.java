package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;

@Data
@Builder
public class BookableSlotResponse {
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean available;
    private int capacity;
    private int bookedCount;
    private int remainingSlots;
}
