package com.PrimeCare.PrimeCare.modules.patient.service;

import com.PrimeCare.PrimeCare.modules.patient.dto.request.CreatePatientRequest;
import com.PrimeCare.PrimeCare.modules.patient.dto.request.UpdatePatientRequest;
import com.PrimeCare.PrimeCare.modules.patient.dto.response.PatientAllergyResponse;
import com.PrimeCare.PrimeCare.modules.patient.dto.response.PatientResponse;
import com.PrimeCare.PrimeCare.modules.patient.dto.response.ReceptionPatientSearchResponse;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientAllergyRepository;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
import com.PrimeCare.PrimeCare.shared.enums.PatientStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PatientService {

    private static final int RECEPTION_SEARCH_MIN_QUERY_LENGTH = 2;
    private static final int RECEPTION_SEARCH_MAX_SIZE = 20;

    private final PatientRepository patientRepository;
    private final PatientAllergyRepository patientAllergyRepository;
    private final DoctorPatientAuthorizationService doctorPatientAuthorizationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public PatientResponse create(CreatePatientRequest req) {
        Patient entity = Patient.builder()
                                .code(generateCode())
                                .fullName(req.getFullName().trim())
                                .phone(req.getPhone().trim())
                                .email(StringUtil.trimToNull(req.getEmail()))
                                .dob(req.getDob())
                                .gender(req.getGender())
                                .address(StringUtil.trimToNull(req.getAddress()))
                                .province(StringUtil.trimToNull(req.getProvince()))
                                .district(StringUtil.trimToNull(req.getDistrict()))
                                .ward(StringUtil.trimToNull(req.getWard()))
                                .identityNumber(StringUtil.trimToNull(req.getIdentityNumber()))
                                .insuranceNumber(StringUtil.trimToNull(req.getInsuranceNumber()))
                                .insuranceExpiryDate(req.getInsuranceExpiryDate())
                                .insuranceRegisteredHospital(StringUtil.trimToNull(req.getInsuranceRegisteredHospital()))
                                .bloodType(req.getBloodType())
                                .ethnicity(StringUtil.trimToNull(req.getEthnicity()))
                                .nationality(req.getNationality() != null ? req.getNationality().trim() : "Việt Nam")
                                .occupation(StringUtil.trimToNull(req.getOccupation()))
                                .emergencyContactName(StringUtil.trimToNull(req.getEmergencyContactName()))
                                .emergencyContactPhone(StringUtil.trimToNull(req.getEmergencyContactPhone()))
                                .allergyNote(StringUtil.trimToNull(req.getAllergyNote()))
                                .chronicDiseaseNote(StringUtil.trimToNull(req.getChronicDiseaseNote()))
                                .note(StringUtil.trimToNull(req.getNote()))
                                .status(PatientStatus.ACTIVE)
                                .build();

        return toResponse(patientRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public PatientResponse get(Long id) {
        return toResponse(
                patientRepository.findById(id)
                                 .orElseThrow(() -> new ApiException(ErrorCode.PATIENT_NOT_FOUND))
        );
    }

    @Transactional(readOnly = true)
    public PatientResponse getForDoctor(Long id, Long doctorUserId) {
        doctorPatientAuthorizationService.requireAccess(id, doctorUserId);
        return get(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> list(String q, PatientStatus status, Pageable pageable) {
        Page<Patient> page = patientRepository.searchAdmin(
                (q != null && !q.isBlank()) ? q.trim() : null,
                status,
                pageable
        );

        var items = page.getContent().stream()
                        .map(this::toResponse)
                        .toList();

        String sort = pageable.getSort().isEmpty() ? null : pageable.getSort().toString();

        return PageResponse.<PatientResponse>builder()
                           .items(items)
                           .meta(PageResponse.Meta.builder()
                                                  .page(page.getNumber())
                                                  .size(page.getSize())
                                                  .totalItems(page.getTotalElements())
                                                  .totalPages(page.getTotalPages())
                                                  .hasNext(page.hasNext())
                                                  .hasPrev(page.hasPrevious())
                                                  .sort(sort)
                                                  .build())
                           .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceptionPatientSearchResponse> searchForReception(String q, Pageable pageable) {
        Pageable safePageable = capReceptionSearchPageable(pageable);
        String keyword = StringUtil.trimToNull(q);
        if (keyword == null || keyword.length() < RECEPTION_SEARCH_MIN_QUERY_LENGTH) {
            return emptyReceptionSearchResponse(safePageable);
        }

        Page<Patient> page = patientRepository.searchForReception(
                keyword,
                PatientStatus.ACTIVE,
                safePageable
        );

        return PageResponse.<ReceptionPatientSearchResponse>builder()
                .items(page.getContent().stream().map(this::toReceptionSearchResponse).toList())
                .meta(PageResponse.Meta.builder()
                        .page(page.getNumber())
                        .size(page.getSize())
                        .totalItems(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .hasNext(page.hasNext())
                        .hasPrev(page.hasPrevious())
                        .sort(safePageable.getSort().isEmpty() ? null : safePageable.getSort().toString())
                        .build())
                .build();
    }

    private Pageable capReceptionSearchPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, 10);
        }
        return PageRequest.of(
                Math.max(0, pageable.getPageNumber()),
                Math.max(1, Math.min(pageable.getPageSize(), RECEPTION_SEARCH_MAX_SIZE)),
                pageable.getSort()
        );
    }

    private PageResponse<ReceptionPatientSearchResponse> emptyReceptionSearchResponse(Pageable pageable) {
        return PageResponse.<ReceptionPatientSearchResponse>builder()
                .items(List.of())
                .meta(PageResponse.Meta.builder()
                        .page(pageable.getPageNumber())
                        .size(pageable.getPageSize())
                        .totalItems(0)
                        .totalPages(0)
                        .hasNext(false)
                        .hasPrev(false)
                        .sort(pageable.getSort().isEmpty() ? null : pageable.getSort().toString())
                        .build())
                .build();
    }

    @Transactional
    public PatientResponse update(Long id, UpdatePatientRequest req) {
        Patient entity = patientRepository.findById(id)
                                          .orElseThrow(() -> new ApiException(ErrorCode.PATIENT_NOT_FOUND));

        if (req.getFullName() != null && !req.getFullName().isBlank()) {
            entity.setFullName(req.getFullName().trim());
        }
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            entity.setPhone(req.getPhone().trim());
        }

        entity.setEmail(normalizeNullableOrKeep(req.getEmail(), entity.getEmail()));
        entity.setDob(req.getDob() != null ? req.getDob() : entity.getDob());
        entity.setGender(req.getGender() != null ? req.getGender() : entity.getGender());
        entity.setAddress(normalizeNullableOrKeep(req.getAddress(), entity.getAddress()));
        entity.setProvince(normalizeNullableOrKeep(req.getProvince(), entity.getProvince()));
        entity.setDistrict(normalizeNullableOrKeep(req.getDistrict(), entity.getDistrict()));
        entity.setWard(normalizeNullableOrKeep(req.getWard(), entity.getWard()));
        entity.setIdentityNumber(normalizeNullableOrKeep(req.getIdentityNumber(), entity.getIdentityNumber()));
        entity.setInsuranceNumber(normalizeNullableOrKeep(req.getInsuranceNumber(), entity.getInsuranceNumber()));
        
        if (req.getInsuranceExpiryDate() != null) {
            entity.setInsuranceExpiryDate(req.getInsuranceExpiryDate());
        }
        
        entity.setInsuranceRegisteredHospital(normalizeNullableOrKeep(req.getInsuranceRegisteredHospital(), entity.getInsuranceRegisteredHospital()));
        
        if (req.getBloodType() != null) {
            entity.setBloodType(req.getBloodType());
        }
        
        entity.setEthnicity(normalizeNullableOrKeep(req.getEthnicity(), entity.getEthnicity()));
        
        if (req.getNationality() != null && !req.getNationality().isBlank()) {
            entity.setNationality(req.getNationality().trim());
        }
        
        entity.setOccupation(normalizeNullableOrKeep(req.getOccupation(), entity.getOccupation()));
        
        entity.setEmergencyContactName(normalizeNullableOrKeep(req.getEmergencyContactName(), entity.getEmergencyContactName()));
        entity.setEmergencyContactPhone(normalizeNullableOrKeep(req.getEmergencyContactPhone(), entity.getEmergencyContactPhone()));
        entity.setAllergyNote(normalizeNullableOrKeep(req.getAllergyNote(), entity.getAllergyNote()));
        entity.setChronicDiseaseNote(normalizeNullableOrKeep(req.getChronicDiseaseNote(), entity.getChronicDiseaseNote()));
        entity.setNote(normalizeNullableOrKeep(req.getNote(), entity.getNote()));

        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }

        return toResponse(patientRepository.save(entity));
    }

    @Transactional
    public Patient findOrCreateFromAppointmentData(
            String fullName,
            String phone,
            String email,
            LocalDate dob,
            Gender gender,
            String note
    ) {
        String normalizedPhone = phone.trim();
        String normalizedFullName = fullName.trim();

        var matchedPatient = dob != null
                ? patientRepository.findByPhoneAndFullNameIgnoreCaseAndDob(normalizedPhone, normalizedFullName, dob)
                : patientRepository.findByPhoneAndFullNameIgnoreCase(normalizedPhone, normalizedFullName);

        return matchedPatient
                .map(existing -> mergeSnapshotIntoPatient(existing, normalizedFullName, email, dob, gender, note))
                .orElseGet(() -> patientRepository.save(
                        Patient.builder()
                               .code(generateCode())
                               .fullName(normalizedFullName)
                               .phone(normalizedPhone)
                               .email(StringUtil.trimToNull(email))
                               .dob(dob)
                               .gender(gender)
                               .note(StringUtil.trimToNull(note))
                               .status(PatientStatus.ACTIVE)
                               .build()
                ));
    }

    private Patient mergeSnapshotIntoPatient(
            Patient patient,
            String fullName,
            String email,
            LocalDate dob,
            Gender gender,
            String note
    ) {
        if (patient.getFullName() == null || patient.getFullName().isBlank()) {
            patient.setFullName(fullName.trim());
        }
        if ((patient.getEmail() == null || patient.getEmail().isBlank()) && email != null && !email.isBlank()) {
            patient.setEmail(email.trim());
        }
        if (patient.getDob() == null && dob != null) {
            patient.setDob(dob);
        }
        if (patient.getGender() == null && gender != null) {
            patient.setGender(gender);
        }
        if ((patient.getNote() == null || patient.getNote().isBlank()) && note != null && !note.isBlank()) {
            patient.setNote(note.trim());
        }
        if (patient.getStatus() == null) {
            patient.setStatus(PatientStatus.ACTIVE);
        }
        return patientRepository.save(patient);
    }

    private String generateCode() {
        String datePart = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now());

        for (int i = 0; i < 20; i++) {
            String suffix = String.format("%04d", secureRandom.nextInt(10_000));
            String code = "PT" + datePart + suffix;
            if (!patientRepository.existsByCode(code)) {
                return code;
            }
        }

        return "PT" + Instant.now().toEpochMilli();
    }

    private PatientResponse toResponse(Patient entity) {
        return PatientResponse.builder()
                              .id(entity.getId())
                              .code(entity.getCode())
                              .fullName(entity.getFullName())
                              .phone(entity.getPhone())
                              .email(entity.getEmail())
                              .dob(entity.getDob())
                              .gender(entity.getGender())
                              .address(entity.getAddress())
                              .province(entity.getProvince())
                              .district(entity.getDistrict())
                              .ward(entity.getWard())
                              .identityNumber(entity.getIdentityNumber())
                              .insuranceNumber(entity.getInsuranceNumber())
                              .insuranceExpiryDate(entity.getInsuranceExpiryDate())
                              .insuranceRegisteredHospital(entity.getInsuranceRegisteredHospital())
                              .bloodType(entity.getBloodType())
                              .ethnicity(entity.getEthnicity())
                              .nationality(entity.getNationality())
                              .occupation(entity.getOccupation())
                              .emergencyContactName(entity.getEmergencyContactName())
                              .emergencyContactPhone(entity.getEmergencyContactPhone())
                              .allergyNote(entity.getAllergyNote())
                              .chronicDiseaseNote(entity.getChronicDiseaseNote())
                              .allergies(patientAllergyRepository.findByPatient_IdOrderByCreatedAtDesc(entity.getId()).stream()
                                      .map(a -> PatientAllergyResponse.builder()
                                              .id(a.getId())
                                              .patientId(entity.getId())
                                              .allergenName(a.getAllergenName())
                                              .allergyType(a.getAllergyType().name())
                                              .severity(a.getSeverity().name())
                                              .reaction(a.getReaction())
                                              .notedById(a.getNotedBy() != null ? a.getNotedBy().getId() : null)
                                              .notedByName(a.getNotedBy() != null ? a.getNotedBy().getFullName() : null)
                                              .createdAt(a.getCreatedAt())
                                              .build())
                                      .toList())
                              .note(entity.getNote())
                              .status(entity.getStatus())
                              .createdAt(entity.getCreatedAt())
                              .updatedAt(entity.getUpdatedAt())
                              .build();
    }

    private ReceptionPatientSearchResponse toReceptionSearchResponse(Patient entity) {
        return ReceptionPatientSearchResponse.builder()
                .id(entity.getId())
                .patientCode(entity.getCode())
                .fullName(entity.getFullName())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .dob(entity.getDob())
                .gender(entity.getGender())
                .address(entity.getAddress())
                .build();
    }

    private String normalizeNullable(String value) {
        if (value == null) return null;
        return value.isBlank() ? null : value.trim();
    }

    private String normalizeNullableOrKeep(String value, String current) {
        if (value == null) return current;
        return value.isBlank() ? null : value.trim();
    }
}
