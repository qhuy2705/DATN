package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.AppointmentContactStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentPatientResponseStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Value
@Builder
public class AppointmentResponseInfoResponse {
    String appointmentCode;
    AppointmentStatus status;
    AppointmentContactStatus contactStatus;
    AppointmentPatientResponseStatus patientResponseStatus;
    String patientFullName;
    String maskedPhone;
    String maskedEmail;
    LocalDate visitDate;
    LocalTime etaStart;
    LocalTime etaEnd;
    String branchName;
    String specialtyName;
    String doctorName;
    LocalDateTime tokenExpiresAt;
    List<String> availableActions;
}
