package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class AppointmentAvailabilityResponse {
    private Long branchId;
    private String branchNameVn;
    private String branchNameEn;
    private Long specialtyId;
    private String specialtyNameVn;
    private String specialtyNameEn;
    private Long doctorId;
    private String doctorName;
    private LocalDate visitDate;
    private BranchSessionType session;
    private Integer slotMinutes;
    private int totalSlots;
    private int bookedSlots;
    private int remainingSlots;
    private List<BookableSlotResponse> slots;
}
