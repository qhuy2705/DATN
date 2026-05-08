package com.PrimeCare.PrimeCare.modules.encounter.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.dto.request.SaveEncounterDiagnosesRequest;
import com.PrimeCare.PrimeCare.modules.encounter.dto.response.EncounterDiagnosisResponse;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.entity.EncounterDiagnosis;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Icd10Code;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterDiagnosisRepository;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.encounter.repository.Icd10CodeRepository;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EncounterDiagnosisService {

    private final EncounterRepository encounterRepository;
    private final EncounterDiagnosisRepository diagnosisRepository;
    private final Icd10CodeRepository icd10CodeRepository;
    private final UserRepository userRepository;

    /**
     * Get all diagnoses for an encounter (grouped by type, ordered by displayOrder).
     */
    @Transactional(readOnly = true)
    public List<EncounterDiagnosisResponse> getDiagnoses(Long encounterId, Long doctorUserId) {
        validateDoctorAccess(encounterId, doctorUserId);

        return diagnosisRepository.findWithIcd10ByEncounterId(encounterId)
                                  .stream()
                                  .map(this::toResponse)
                                  .toList();
    }

    /**
     * Save (replace) diagnoses of a specific type for an encounter.
     * Replaces all existing diagnoses of that type with the new list.
     */
    @Transactional
    public List<EncounterDiagnosisResponse> saveDiagnoses(
            Long encounterId,
            Long doctorUserId,
            SaveEncounterDiagnosesRequest request
    ) {
        Encounter encounter = validateDoctorAccess(encounterId, doctorUserId);

        if (encounter.getStatus() == EncounterStatus.COMPLETED || encounter.getStatus() == EncounterStatus.CANCELLED) {
            throw new ApiException(ErrorCode.ENCOUNTER_INVALID_STATUS, "Lần khám đã kết thúc, không thể cập nhật chẩn đoán");
        }

        // Validate all ICD-10 codes exist
        List<SaveEncounterDiagnosesRequest.DiagnosisItem> requestedItems = request.getItems() != null
                ? request.getItems()
                : List.of();

        List<Long> codeIds = requestedItems.stream()
                                    .map(SaveEncounterDiagnosesRequest.DiagnosisItem::getIcd10CodeId)
                                    .toList();

        Map<Long, Icd10Code> codeMap = codeIds.isEmpty()
                ? Map.of()
                : icd10CodeRepository.findByIdIn(codeIds)
                                      .stream()
                                      .collect(Collectors.toMap(Icd10Code::getId, Function.identity()));

        for (Long codeId : codeIds) {
            if (!codeMap.containsKey(codeId)) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Mã ICD-10 không tồn tại: ID=" + codeId);
            }
            Icd10Code code = codeMap.get(codeId);
            if (!code.isActive()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Mã ICD-10 đã ngưng sử dụng: " + code.getCode());
            }
        }

        // Delete existing diagnoses of this type
        diagnosisRepository.deleteByEncounter_IdAndDiagnosisType(encounterId, request.getDiagnosisType());

        // Create new diagnoses
        List<EncounterDiagnosis> newDiagnoses = new ArrayList<>();
        int order = 0;
        for (var item : requestedItems) {
            Icd10Code icd10Code = codeMap.get(item.getIcd10CodeId());
            newDiagnoses.add(EncounterDiagnosis.builder()
                                               .encounter(encounter)
                                               .icd10Code(icd10Code)
                                               .diagnosisType(request.getDiagnosisType())
                                               .note(item.getNote())
                                               .displayOrder(item.getDisplayOrder() > 0 ? item.getDisplayOrder() : order++)
                                               .build());
        }

        List<EncounterDiagnosis> saved = diagnosisRepository.saveAll(newDiagnoses);

        // Also update the text fields on encounter for backward compatibility
        syncEncounterDiagnosisText(encounter);

        return saved.stream().map(this::toResponse).toList();
    }

    /**
     * Sync the free-text diagnosis fields on Encounter from structured ICD-10 data.
     */
    private void syncEncounterDiagnosisText(Encounter encounter) {
        String preliminaryText = buildDiagnosisText(encounter.getId(), EncounterDiagnosis.DiagnosisType.PRELIMINARY);
        String finalText = buildDiagnosisText(encounter.getId(), EncounterDiagnosis.DiagnosisType.FINAL);
        String secondaryText = buildDiagnosisText(encounter.getId(), EncounterDiagnosis.DiagnosisType.SECONDARY);

        encounter.setPreliminaryDiagnosis(preliminaryText.isEmpty() ? null : preliminaryText);
        if (secondaryText.isEmpty()) {
            encounter.setFinalDiagnosis(finalText.isEmpty() ? null : finalText);
        } else {
            encounter.setFinalDiagnosis(
                    finalText.isEmpty()
                            ? "Phụ: " + secondaryText
                            : finalText + " | Phụ: " + secondaryText
            );
        }
        encounterRepository.save(encounter);
    }

    private String buildDiagnosisText(Long encounterId, EncounterDiagnosis.DiagnosisType type) {
        return diagnosisRepository
                .findByEncounter_IdAndDiagnosisTypeOrderByDisplayOrderAsc(encounterId, type)
                .stream()
                .map(d -> d.getIcd10Code().getCode() + " - " + d.getIcd10Code().getNameVn()
                        + (d.getNote() != null ? " (" + d.getNote() + ")" : ""))
                .collect(Collectors.joining("; "));
    }

    private Encounter validateDoctorAccess(Long encounterId, Long doctorUserId) {
        User doctorUser = userRepository.findById(doctorUserId)
                                        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        if (doctorUser.getDoctorProfile() == null || doctorUser.getDoctorProfile().getId() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Bạn không thuộc bác sĩ phụ trách lần khám này");
        }

        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ApiException(ErrorCode.ENCOUNTER_NOT_FOUND));
        Long encounterDoctorId = encounter.getDoctor() != null ? encounter.getDoctor().getId() : null;
        if (!doctorUser.getDoctorProfile().getId().equals(encounterDoctorId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Bác sĩ chỉ được truy cập hồ sơ khám do mình phụ trách");
        }
        return encounter;
    }

    private EncounterDiagnosisResponse toResponse(EncounterDiagnosis d) {
        return EncounterDiagnosisResponse.builder()
                                         .id(d.getId())
                                         .icd10CodeId(d.getIcd10Code().getId())
                                         .icd10Code(d.getIcd10Code().getCode())
                                         .icd10NameVn(d.getIcd10Code().getNameVn())
                                         .icd10NameEn(d.getIcd10Code().getNameEn())
                                         .diagnosisType(d.getDiagnosisType())
                                         .note(d.getNote())
                                         .displayOrder(d.getDisplayOrder())
                                         .build();
    }
}
