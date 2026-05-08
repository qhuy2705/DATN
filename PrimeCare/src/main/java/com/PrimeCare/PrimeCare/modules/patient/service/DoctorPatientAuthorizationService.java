package com.PrimeCare.PrimeCare.modules.patient.service;

import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.patient.entity.PatientAllergy;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientAllergyRepository;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientRepository;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DoctorPatientAuthorizationService {

    private static final Set<AppointmentStatus> ACCESSIBLE_APPOINTMENT_STATUSES = EnumSet.of(
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.COMPLETED
    );

    private static final Set<EncounterStatus> ACCESSIBLE_ENCOUNTER_STATUSES = EnumSet.of(
            EncounterStatus.IN_PROGRESS,
            EncounterStatus.WAITING_PAYMENT,
            EncounterStatus.WAITING_RESULTS,
            EncounterStatus.READY_FOR_CONCLUSION,
            EncounterStatus.REOPENED,
            EncounterStatus.COMPLETED
    );

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PatientAllergyRepository patientAllergyRepository;
    private final AppointmentRepository appointmentRepository;
    private final EncounterRepository encounterRepository;

    @Transactional(readOnly = true)
    public void requireAccess(Long patientId, Long doctorUserId) {
        if (!patientRepository.existsById(patientId)) {
            throw new ApiException(ErrorCode.PATIENT_NOT_FOUND);
        }

        Long doctorProfileId = resolveDoctorProfileId(doctorUserId);
        boolean hasAppointmentAccess = appointmentRepository.existsByDoctor_IdAndPatient_IdAndStatusIn(
                doctorProfileId,
                patientId,
                ACCESSIBLE_APPOINTMENT_STATUSES
        );
        if (hasAppointmentAccess) {
            return;
        }

        boolean hasEncounterAccess = encounterRepository.existsByDoctor_IdAndPatient_IdAndStatusIn(
                doctorProfileId,
                patientId,
                ACCESSIBLE_ENCOUNTER_STATUSES
        );
        if (!hasEncounterAccess) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập hồ sơ bệnh nhân này");
        }
    }

    @Transactional(readOnly = true)
    public void requireAllergyAccess(Long allergyId, Long doctorUserId) {
        PatientAllergy allergy = patientAllergyRepository.findById(allergyId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Không tìm thấy dị ứng"));
        requireAccess(allergy.getPatient().getId(), doctorUserId);
    }

    private Long resolveDoctorProfileId(Long doctorUserId) {
        User doctorUser = userRepository.findById(doctorUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (doctorUser.getDoctorProfile() == null || doctorUser.getDoctorProfile().getId() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Bạn không phải bác sĩ phụ trách");
        }
        return doctorUser.getDoctorProfile().getId();
    }
}
