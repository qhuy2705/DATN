package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.CreateAppointmentRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.event.AppointmentCreatedSpringEvent;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.entity.DoctorWorkSchedule;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.BranchSession;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchSessionRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.DoctorOperationalGuardService;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient.service.PatientService;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.BranchSpecialtyService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final EnumSet<AppointmentStatus> BLOCKING_STATUSES = EnumSet.of(
            AppointmentStatus.REQUESTED,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN
    );

    private final AppointmentRepository appointmentRepository;
    private final AppointmentAvailabilityService availabilityService;
    private final BranchRepository branchRepository;
    private final SpecialtyRepository specialtyRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final BranchSpecialtyService branchSpecialtyService;
    private final DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    private final BranchSessionRepository branchSessionRepository;
    private final AppointmentCodeGenerator appointmentCodeGenerator;
    private final PatientService patientService;
    private final DoctorOperationalGuardService doctorOperationalGuardService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        if (request.getVisitDate().isBefore(LocalDate.now())) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_DATE);
        }

        Branch branch = branchRepository.findById(request.getBranchId())
                                        .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));

        Specialty specialty = specialtyRepository.findById(request.getSpecialtyId())
                                                 .orElseThrow(() -> new ApiException(ErrorCode.SPECIALTY_NOT_FOUND));

        DoctorProfile doctor = doctorProfileRepository.findByIdAndBranch_Id(
                                                              request.getDoctorId(),
                                                              request.getBranchId()
                                                      )
                                                      .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));

        branchSpecialtyService.validateBranchSpecialtyActive(request.getBranchId(), request.getSpecialtyId());

        doctorOperationalGuardService.assertDoctorBookable(doctor);

        if (!doctorProfileRepository.existsDoctorSpecialty(doctor.getId(), specialty.getId())) {
            throw new ApiException(ErrorCode.SPECIALTY_NOT_FOUND, "Bác sĩ không thuộc chuyên khoa đã chọn");
        }

        DoctorWorkSchedule lockedSchedule = doctorWorkScheduleRepository
                .findWithLockByDoctor_IdAndWorkDateAndSession(
                        request.getDoctorId(),
                        request.getVisitDate(),
                        request.getSession()
                )
                .orElseThrow(() -> new ApiException(
                        ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                        "Bác sĩ không có lịch làm việc trong buổi đã chọn"
                ));

        BranchSession branchSession = branchSessionRepository
                .findByBranch_IdAndSessionAndStatus(request.getBranchId(), request.getSession(), "ACTIVE")
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Chi nhánh chưa mở buổi khám này"));

        AppointmentAvailabilityResponse availability = availabilityService.getAvailability(
                request.getBranchId(),
                request.getSpecialtyId(),
                request.getDoctorId(),
                request.getVisitDate(),
                request.getSession(),
                false
        );

        var selectedSlot = availability.getSlots().stream()
                                       .filter(slot -> slot.getStartTime().equals(request.getSlotStart()))
                                       .findFirst()
                                       .orElseThrow(() -> new ApiException(
                                               ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                                               "Khung giờ không nằm trong buổi khám"
                                       ));

        availabilityService.assertSlotStillBookable(request.getVisitDate(), request.getSlotStart());

        if (!selectedSlot.isAvailable()) {
            throw new ApiException(
                    ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                    "Khung giờ đã có lịch hẹn. Vui lòng chọn khung giờ khác."
            );
        }

        int slotCapacity = availabilityService.resolveSlotCapacity(branchSession);

        var blockingAppointments = appointmentRepository.findWithLockByDoctor_IdAndVisitDateAndSessionAndStatusIn(
                doctor.getId(),
                request.getVisitDate(),
                request.getSession(),
                BLOCKING_STATUSES
        );

        long bookingCount = availabilityService.countOverlappingAppointments(
                blockingAppointments,
                selectedSlot.getStartTime(),
                selectedSlot.getEndTime()
        );

        if (bookingCount >= slotCapacity) {
            throw new ApiException(
                    ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE,
                    "Khung giờ đã có lịch hẹn. Vui lòng chọn khung giờ khác."
            );
        }

        if (request.getPatientDob() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Ngày sinh không được để trống");
        }
        if (request.getPatientGender() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Giới tính không được để trống");
        }
        if (StringUtil.trimToNull(request.getVisitType()) == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Loại lượt khám không được để trống");
        }

        Patient patient = patientService.findOrCreateFromAppointmentData(
                request.getPatientFullName(),
                request.getPatientPhone(),
                request.getPatientEmail(),
                request.getPatientDob(),
                request.getPatientGender(),
                request.getPatientNote()
        );

        boolean duplicateBooking = appointmentRepository.existsByDoctor_IdAndVisitDateAndSessionAndEtaStartAndPatientPhoneAndStatusIn(
                doctor.getId(),
                request.getVisitDate(),
                request.getSession(),
                request.getSlotStart(),
                request.getPatientPhone().trim(),
                BLOCKING_STATUSES
        );
        if (duplicateBooking || hasSamePatientSameSlotBooking(
                blockingAppointments,
                patient,
                request.getPatientFullName(),
                request.getPatientDob(),
                request.getPatientGender(),
                request.getSlotStart()
        )) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Ban da co lich hen trung khung gio voi bac si nay");
        }

        String code = appointmentCodeGenerator.generate(request.getVisitDate(), doctor.getId(), request.getSlotStart());

        Appointment entity = Appointment.builder()
                                        .code(code)
                                        .status(AppointmentStatus.REQUESTED)
                                        .branch(branch)
                                        .specialty(specialty)
                                        .doctor(doctor)
                                        .visitDate(request.getVisitDate())
                                        .session(request.getSession())
                                        .queueNo(null)
                                        .slotMinutes(availability.getSlotMinutes())
                                        .etaStart(selectedSlot.getStartTime())
                                        .etaEnd(selectedSlot.getEndTime())
                                        .sourceType(AppointmentSourceType.PUBLIC_BOOKING)
                                        .arrivalStatus(ArrivalStatus.NOT_ARRIVED)
                                        .patientFullName(request.getPatientFullName().trim())
                                        .patientPhone(request.getPatientPhone().trim())
                                        .patientEmail(StringUtil.trimToNull(request.getPatientEmail()))
                                        .patient(patient)
                                        .patientDob(request.getPatientDob())
                                        .patientGender(request.getPatientGender())
                                        .patientNote(StringUtil.trimToNull(request.getPatientNote()))
                                        .reasonForVisit(StringUtil.trimToNull(request.getReasonForVisit()))
                                        .visitType(StringUtil.trimToNull(request.getVisitType()) != null ? request.getVisitType().trim().toUpperCase().replace(' ', '_') : null)
                                        .build();

        Appointment saved = appointmentRepository.save(entity);
        availabilityService.evictAvailabilityCacheForDoctorDateSession(
                saved.getDoctor().getId(),
                saved.getVisitDate(),
                saved.getSession()
        );

        applicationEventPublisher.publishEvent(new AppointmentCreatedSpringEvent(
                saved.getId(),
                saved.getBranch() != null ? saved.getBranch().getId() : null,
                saved.getVisitDate(),
                saved.getCode()
        ));

        return toResponse(saved);
    }

    private boolean hasSamePatientSameSlotBooking(
            List<Appointment> appointments,
            Patient patient,
            String patientFullName,
            LocalDate patientDob,
            com.PrimeCare.PrimeCare.shared.enums.Gender patientGender,
            java.time.LocalTime slotStart
    ) {
        String normalizedName = normalizePatientName(patientFullName);

        return appointments.stream()
                           .filter(existing -> slotStart.equals(existing.getEtaStart()))
                           .anyMatch(existing -> isSamePatient(existing, patient, normalizedName, patientDob, patientGender));
    }

    private boolean isSamePatient(
            Appointment existing,
            Patient patient,
            String normalizedName,
            LocalDate patientDob,
            com.PrimeCare.PrimeCare.shared.enums.Gender patientGender
    ) {
        if (patient != null
                && patient.getId() != null
                && existing.getPatient() != null
                && patient.getId().equals(existing.getPatient().getId())) {
            return true;
        }

        return normalizedName != null
                && patientDob != null
                && patientGender != null
                && normalizedName.equals(normalizePatientName(existing.getPatientFullName()))
                && patientDob.equals(existing.getPatientDob())
                && patientGender == existing.getPatientGender();
    }

    private String normalizePatientName(String value) {
        String trimmed = StringUtil.trimToNull(value);
        return trimmed != null ? trimmed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") : null;
    }

    private AppointmentResponse toResponse(Appointment entity) {
        return AppointmentResponse.builder()
                                  .id(entity.getId())
                                  .code(entity.getCode())
                                  .status(entity.getStatus())
                                  .branchId(entity.getBranch().getId())
                                  .branchNameVn(entity.getBranch().getNameVn())
                                  .branchNameEn(entity.getBranch().getNameEn())
                                  .specialtyId(entity.getSpecialty().getId())
                                  .specialtyNameVn(entity.getSpecialty().getNameVn())
                                  .specialtyNameEn(entity.getSpecialty().getNameEn())
                                  .doctorId(entity.getDoctor() != null ? entity.getDoctor().getId() : null)
                                  .doctorName(entity.getDoctor() != null ? entity.getDoctor().getFullName() : null)
                                  .visitDate(entity.getVisitDate())
                                  .session(entity.getSession())
                                  .queueNo(entity.getQueueNo())
                                  .slotMinutes(entity.getSlotMinutes())
                                  .etaStart(entity.getEtaStart())
                                  .etaEnd(entity.getEtaEnd())
                                  .sourceType(entity.getSourceType())
                                  .arrivalStatus(entity.getArrivalStatus())
                                  .receptionQueueNo(entity.getReceptionQueueNo())
                                  .arrivedAt(entity.getArrivedAt())
                                  .patientFullName(entity.getPatientFullName())
                                  .patientPhone(entity.getPatientPhone())
                                  .patientEmail(entity.getPatientEmail())
                                  .patientDob(entity.getPatientDob())
                                  .patientId(entity.getPatient() != null ? entity.getPatient().getId() : null)
                                  .patientGender(entity.getPatientGender())
                                  .patientNote(entity.getPatientNote())
                                  .reasonForVisit(entity.getReasonForVisit())
                                  .visitType(entity.getVisitType())
                                  .build();
    }
}
