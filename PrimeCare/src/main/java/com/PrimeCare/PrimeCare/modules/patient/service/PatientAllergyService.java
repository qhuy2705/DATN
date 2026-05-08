package com.PrimeCare.PrimeCare.modules.patient.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.patient.dto.response.PatientAllergyResponse;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient.entity.PatientAllergy;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientAllergyRepository;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientRepository;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PatientAllergyService {

    private final PatientAllergyRepository allergyRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final DoctorPatientAuthorizationService doctorPatientAuthorizationService;

    @Transactional
    public PatientAllergyResponse createForDoctor(Long patientId, Long doctorId, String allergenName, PatientAllergy.AllergyType type, PatientAllergy.AllergySeverity severity, String reaction) {
        doctorPatientAuthorizationService.requireAccess(patientId, doctorId);
        return create(patientId, doctorId, allergenName, type, severity, reaction);
    }

    private PatientAllergyResponse create(Long patientId, Long doctorId, String allergenName, PatientAllergy.AllergyType type, PatientAllergy.AllergySeverity severity, String reaction) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ApiException(ErrorCode.PATIENT_NOT_FOUND));

        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Bác sĩ không hợp lệ"));

        PatientAllergy allergy = PatientAllergy.builder()
                .patient(patient)
                .allergenName(allergenName.trim())
                .allergyType(type)
                .severity(severity)
                .reaction(reaction != null ? reaction.trim() : null)
                .notedBy(doctor)
                .build();

        return toResponse(allergyRepository.save(allergy));
    }

    @Transactional
    public void deleteForDoctor(Long id, Long doctorId) {
        doctorPatientAuthorizationService.requireAllergyAccess(id, doctorId);
        delete(id);
    }

    private void delete(Long id) {
        PatientAllergy allergy = allergyRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Không tìm thấy dị ứng"));
        allergyRepository.delete(allergy);
    }

    @Transactional(readOnly = true)
    public List<PatientAllergyResponse> listForDoctor(Long patientId, Long doctorId) {
        doctorPatientAuthorizationService.requireAccess(patientId, doctorId);
        return list(patientId);
    }

    private List<PatientAllergyResponse> list(Long patientId) {
        return allergyRepository.findByPatient_IdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private PatientAllergyResponse toResponse(PatientAllergy a) {
        return PatientAllergyResponse.builder()
                .id(a.getId())
                .patientId(a.getPatient().getId())
                .allergenName(a.getAllergenName())
                .allergyType(a.getAllergyType().name())
                .severity(a.getSeverity().name())
                .reaction(a.getReaction())
                .notedById(a.getNotedBy() != null ? a.getNotedBy().getId() : null)
                .notedByName(a.getNotedBy() != null ? a.getNotedBy().getFullName() : null)
                .createdAt(a.getCreatedAt())
                .build();
    }
}
