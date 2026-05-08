package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class AppointmentResponse {
    private Long id;
    private String code;
    private AppointmentStatus status;
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
    private Integer queueNo;
    private Integer slotMinutes;
    private LocalTime etaStart;
    private LocalTime etaEnd;
    private String patientFullName;
    private String patientPhone;
    private String patientEmail;
    private String reasonForVisit;
    private String visitType;
    private LocalDate patientDob;
    private Gender patientGender;
    private String patientNote;
    private Long patientId;
    private AppointmentSourceType sourceType;
    private ArrivalStatus arrivalStatus;
    private Integer receptionQueueNo;
    private LocalDateTime arrivedAt;
}
