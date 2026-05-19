package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;

import java.time.LocalDate;
import java.time.LocalTime;

public record AlternativeSlotSuggestion(
        DoctorProfile doctor,
        LocalDate visitDate,
        BranchSessionType session,
        LocalTime slotStart,
        LocalTime slotEnd
) {
}
