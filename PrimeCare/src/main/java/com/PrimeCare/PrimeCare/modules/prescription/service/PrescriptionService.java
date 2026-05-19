package com.PrimeCare.PrimeCare.modules.prescription.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.dto.record.EncounterWorkflowState;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterWorkflowService;
import com.PrimeCare.PrimeCare.modules.medication.entity.DrugInteraction;
import com.PrimeCare.PrimeCare.modules.medication.entity.Medication;
import com.PrimeCare.PrimeCare.modules.medication.repository.DrugInteractionRepository;
import com.PrimeCare.PrimeCare.modules.medication.repository.MedicationRepository;
import com.PrimeCare.PrimeCare.modules.patient.entity.PatientAllergy;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientAllergyRepository;
import com.PrimeCare.PrimeCare.modules.pharmacy.service.InventoryService;
import com.PrimeCare.PrimeCare.modules.prescription.dto.request.CreatePrescriptionRequest;
import com.PrimeCare.PrimeCare.modules.prescription.dto.request.UpdatePrescriptionRequest;
import com.PrimeCare.PrimeCare.modules.prescription.dto.response.PrescriptionItemResponse;
import com.PrimeCare.PrimeCare.modules.prescription.dto.response.PrescriptionResponse;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.prescription.entity.PrescriptionItem;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.MedicationStatus;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrescriptionService {

    private static final int PHARMACY_SEARCH_MAX_LENGTH = 128;

    private final PrescriptionRepository prescriptionRepository;
    private final EncounterRepository encounterRepository;
    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;
    private final PatientAllergyRepository patientAllergyRepository;
    private final DrugInteractionRepository drugInteractionRepository;
    private final PrescriptionPdfService prescriptionPdfService;
    private final AuditLogService auditLogService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final AfterCommitExecutor afterCommitExecutor;
    private final InventoryService inventoryService;
    private final EncounterWorkflowService encounterWorkflowService;

    @Transactional
    public PrescriptionResponse create(Long encounterId, Long doctorUserId, CreatePrescriptionRequest req) {
        Encounter encounter = encounterRepository.findById(encounterId)
                                                 .orElseThrow(() -> new ApiException(ErrorCode.ENCOUNTER_NOT_FOUND));

        validateEncounterForPrescription(encounter);

        User doctorUser = validateDoctorOwnership(encounter, doctorUserId);

        if (prescriptionRepository.existsByEncounter_IdAndStatus(encounterId, PrescriptionStatus.ISSUED)) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Lần khám này đã có đơn thuốc đang hiệu lực");
        }

        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Đơn thuốc phải có ít nhất 1 thuốc");
        }

        Prescription prescription = Prescription.builder()
                                                .code(generateCode(encounter))
                                                .encounter(encounter)
                                                .doctorUser(doctorUser)
                                                .issuedDate(LocalDate.now())
                                                .generalNote(StringUtil.trimToNull(req.getGeneralNote()))
                                                .status(PrescriptionStatus.ISSUED)
                                                .items(new ArrayList<>())
                                                .build();

        List<PatientAllergy> patientAllergies = patientAllergyRepository.findByPatient_IdOrderByCreatedAtDesc(encounter.getPatient().getId());

        for (var i : req.getItems()) {
            Medication medication = medicationRepository.findById(i.getMedicationId())
                                                        .orElseThrow(() -> new ApiException(ErrorCode.MEDICATION_NOT_FOUND));

            ensureMedicationActive(medication);
            validateMedicationSafety(medication, patientAllergies, prescription.getItems());

            PrescriptionItem item = PrescriptionItem.builder()
                                                    .prescription(prescription)
                                                    .medication(medication)
                                                    .medicationCodeSnapshot(medication.getCode())
                                                    .medicationNameSnapshot(medication.getName())
                                                    .strengthSnapshot(medication.getStrength())
                                                    .dosageFormSnapshot(medication.getDosageForm())
                                                    .unitSnapshot(medication.getUnit())
                                                    .quantity(i.getQuantity())
                                                    .dose(StringUtil.trimToNull(i.getDose()))
                                                    .frequency(StringUtil.trimToNull(i.getFrequency()))
                                                    .durationDays(i.getDurationDays())
                                                    .route(StringUtil.trimToNull(i.getRoute()))
                                                    .instruction(StringUtil.trimToNull(i.getInstruction()))
                                                    .build();

            prescription.getItems().add(item);
        }

        Prescription saved = prescriptionRepository.save(prescription);
        auditLogService.log(doctorUser, "CREATE_PRESCRIPTION", "PRESCRIPTION", saved.getId(), null, snapshotPrescription(saved));
        afterCommitExecutor.execute(() -> publishPrescriptionRealtime(encounter, "PRESCRIPTION_CREATED"));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<PrescriptionResponse> listByEncounter(Long encounterId, Long doctorUserId, Pageable pageable) {
        Encounter encounter = encounterRepository.findById(encounterId)
                                                 .orElseThrow(() -> new ApiException(ErrorCode.ENCOUNTER_NOT_FOUND));
        validateDoctorOwnership(encounter, doctorUserId);

        Page<Prescription> page = prescriptionRepository.findByEncounter_IdOrderByCreatedAtDesc(encounterId, pageable);

        var items = page.getContent().stream()
                        .map(this::toResponse)
                        .toList();

        String sort = pageable.getSort().isEmpty() ? null : pageable.getSort().toString();

        return PageResponse.<PrescriptionResponse>builder()
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
    public PageResponse<PrescriptionResponse> listByStatus(PrescriptionStatus status, Pageable pageable) {
        return listForPharmacy(status, null, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<PrescriptionResponse> listForPharmacy(PrescriptionStatus status, String q, Pageable pageable) {
        long started = System.nanoTime();
        String keyword = normalizePharmacyKeyword(q);
        String keywordLike = keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        String keywordPrefix = keyword == null ? null : keyword + "%";
        Long keywordId = parseKeywordId(keyword);

        Page<Long> page = prescriptionRepository.findIdsForPharmacy(status, keywordLike, keywordPrefix, keyword, keywordId, pageable);
        List<Long> ids = page.getContent();

        Map<Long, Prescription> prescriptionsById = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            prescriptionRepository.findAllWithDetailsByIdIn(ids)
                                  .forEach(prescription -> prescriptionsById.put(prescription.getId(), prescription));
        }

        var items = ids.stream()
                        .map(prescriptionsById::get)
                        .filter(Objects::nonNull)
                        .map(this::toResponse)
                        .toList();

        String sort = pageable.getSort().isEmpty() ? null : pageable.getSort().toString();

        PageResponse<PrescriptionResponse> response = PageResponse.<PrescriptionResponse>builder()
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
        log.info("pharmacy prescriptions durationMs={} status={} size={}",
                (System.nanoTime() - started) / 1_000_000L, status, page.getNumberOfElements());
        return response;
    }

    @Transactional
    public PrescriptionResponse markAsDispensed(Long prescriptionId, Long dispenserUserId) {
        Prescription prescription = prescriptionRepository.findWithLockDetailsById(prescriptionId)
                .orElseThrow(() -> new ApiException(ErrorCode.PRESCRIPTION_NOT_FOUND));

        if (prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Prescription has already been dispensed.");
        }
        if (prescription.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Đơn thuốc đã hủy, không thể phát thuốc.");
        }
        if (prescription.getStatus() != PrescriptionStatus.PAID) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Đơn thuốc chưa thanh toán, không thể phát thuốc.");
        }

        User dispenser = userRepository.findById(dispenserUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        Map<String, Object> before = snapshotPrescription(prescription);
        List<PrescriptionItem> activeItems = activeDispensableItems(prescription);
        if (activeItems.isEmpty()) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Đơn thuốc không còn thuốc hợp lệ để phát.");
        }

        Map<Long, Integer> requiredQuantityByMedication = requiredQuantityByMedication(activeItems);

        requiredQuantityByMedication.forEach(inventoryService::validateDispenseAvailability);

        for (PrescriptionItem item : activeItems) {
            inventoryService.dispenseFIFO(
                    item.getMedication().getId(),
                    item.getQuantity(),
                    prescription.getId(),
                    dispenserUserId
            );
            item.setStatus(PrescriptionItemStatus.DISPENSED);
        }

        prescription.setStatus(PrescriptionStatus.DISPENSED);
        
        Prescription saved = prescriptionRepository.save(prescription);
        auditLogService.log(dispenser, "DISPENSE_PRESCRIPTION", "PRESCRIPTION", saved.getId(), before, snapshotPrescription(saved));

        return toResponse(saved);
    }

    private List<PrescriptionItem> activeDispensableItems(Prescription prescription) {
        if (prescription.getItems() == null) {
            return List.of();
        }
        return prescription.getItems().stream()
                .filter(item -> item.getStatus() != PrescriptionItemStatus.REFUNDED
                        && item.getStatus() != PrescriptionItemStatus.CANCELLED
                        && item.getStatus() != PrescriptionItemStatus.DISPENSED)
                .toList();
    }

    private Map<Long, Integer> requiredQuantityByMedication(List<PrescriptionItem> items) {
        if (items == null || items.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Đơn thuốc không có thuốc để phát");
        }

        Map<Long, Integer> requiredQuantityByMedication = new LinkedHashMap<>();
        for (PrescriptionItem item : items) {
            if (item.getMedication() == null
                    || item.getMedication().getId() == null
                    || item.getQuantity() == null
                    || item.getQuantity() <= 0) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Số lượng thuốc trong đơn không hợp lệ");
            }
            requiredQuantityByMedication.merge(item.getMedication().getId(), item.getQuantity(), Integer::sum);
        }
        return requiredQuantityByMedication;
    }

    private String normalizePharmacyKeyword(String q) {
        String trimmed = StringUtil.trimToNull(q);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > PHARMACY_SEARCH_MAX_LENGTH) {
            trimmed = trimmed.substring(0, PHARMACY_SEARCH_MAX_LENGTH);
        }
        return trimmed;
    }

    private Long parseKeywordId(String keyword) {
        if (keyword == null || keyword.chars().anyMatch(ch -> !Character.isDigit(ch))) {
            return null;
        }
        try {
            return Long.valueOf(keyword);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Transactional
    public PrescriptionResponse update(Long prescriptionId, Long doctorUserId, UpdatePrescriptionRequest req) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                                                          .orElseThrow(() -> new ApiException(ErrorCode.PRESCRIPTION_NOT_FOUND));

        validateEncounterForPrescription(prescription.getEncounter());
        User doctorUser = validateDoctorOwnership(prescription.getEncounter(), doctorUserId);

        if (prescription.getStatus() != PrescriptionStatus.ISSUED
                && prescription.getStatus() != PrescriptionStatus.DRAFT) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Chỉ được sửa đơn ở trạng thái DRAFT hoặc ISSUED");
        }

        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Đơn thuốc phải có ít nhất 1 thuốc");
        }

        Map<String, Object> before = snapshotPrescription(prescription);
        List<PatientAllergy> patientAllergies = patientAllergyRepository.findByPatient_IdOrderByCreatedAtDesc(
                prescription.getEncounter().getPatient().getId()
        );

        prescription.setGeneralNote(StringUtil.trimToNull(req.getGeneralNote()));
        prescription.getItems().clear();

        for (var i : req.getItems()) {
            Medication medication = medicationRepository.findById(i.getMedicationId())
                                                        .orElseThrow(() -> new ApiException(ErrorCode.MEDICATION_NOT_FOUND));

            ensureMedicationActive(medication);
            validateMedicationSafety(medication, patientAllergies, prescription.getItems());

            PrescriptionItem item = PrescriptionItem.builder()
                                                    .prescription(prescription)
                                                    .medication(medication)
                                                    .medicationCodeSnapshot(medication.getCode())
                                                    .medicationNameSnapshot(medication.getName())
                                                    .strengthSnapshot(medication.getStrength())
                                                    .dosageFormSnapshot(medication.getDosageForm())
                                                    .unitSnapshot(medication.getUnit())
                                                    .quantity(i.getQuantity())
                                                    .dose(StringUtil.trimToNull(i.getDose()))
                                                    .frequency(StringUtil.trimToNull(i.getFrequency()))
                                                    .durationDays(i.getDurationDays())
                                                    .route(StringUtil.trimToNull(i.getRoute()))
                                                    .instruction(StringUtil.trimToNull(i.getInstruction()))
                                                    .build();

            prescription.getItems().add(item);
        }

        Prescription saved = prescriptionRepository.save(prescription);
        auditLogService.log(doctorUser, "UPDATE_PRESCRIPTION", "PRESCRIPTION", saved.getId(), before, snapshotPrescription(saved));
        afterCommitExecutor.execute(() -> publishPrescriptionRealtime(saved.getEncounter(), "PRESCRIPTION_UPDATED"));

        return toResponse(saved);
    }

    @Transactional
    public PrescriptionResponse cancel(Long prescriptionId, Long doctorUserId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                                                          .orElseThrow(() -> new ApiException(ErrorCode.PRESCRIPTION_NOT_FOUND));

        User doctorUser = validateDoctorOwnership(prescription.getEncounter(), doctorUserId);

        if (prescription.getStatus() == PrescriptionStatus.CANCELLED) {
            return toResponse(prescription);
        }

        if (prescription.getStatus() != PrescriptionStatus.DRAFT
                && prescription.getStatus() != PrescriptionStatus.ISSUED) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Chỉ được hủy đơn thuốc ở trạng thái DRAFT hoặc ISSUED");
        }

        Map<String, Object> before = snapshotPrescription(prescription);

        prescription.setStatus(PrescriptionStatus.CANCELLED);
        Prescription saved = prescriptionRepository.save(prescription);

        auditLogService.log(doctorUser, "CANCEL_PRESCRIPTION", "PRESCRIPTION", saved.getId(), before, snapshotPrescription(saved));
        afterCommitExecutor.execute(() -> publishPrescriptionRealtime(saved.getEncounter(), "PRESCRIPTION_CANCELLED"));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf(Long prescriptionId, Long doctorUserId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                                                          .orElseThrow(() -> new ApiException(ErrorCode.PRESCRIPTION_NOT_FOUND));

        validateDoctorOwnership(prescription.getEncounter(), doctorUserId);

        return prescriptionPdfService.generate(prescription);
    }

    private void validateEncounterForPrescription(Encounter encounter) {
        if (encounter.getStatus() == EncounterStatus.CANCELLED || encounter.getStatus() == EncounterStatus.COMPLETED) {
            throw new ApiException(ErrorCode.ENCOUNTER_INVALID_STATUS);
        }

        EncounterWorkflowState workflowState = encounterWorkflowService.getWorkflowState(encounter);
        if (workflowState.hasPendingPayment()) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Cần hoàn tất thanh toán dịch vụ trước khi kê đơn");
        }

        if (workflowState.hasWaitingResults()) {
            throw new ApiException(ErrorCode.PRESCRIPTION_INVALID_STATUS, "Đang chờ kết quả dịch vụ, chưa thể kê đơn");
        }
    }

    private void publishPrescriptionRealtime(Encounter encounter, String event) {
        realtimeEventPublisher.publishEncounterChannel(encounter.getId(), event);
        if (encounter.getDoctor() != null && encounter.getDoctor().getId() != null) {
            realtimeEventPublisher.publishDoctorEncounterUpdated(
                    encounter.getDoctor().getId(),
                    encounter.getId(),
                    encounter.getStatus() != null ? encounter.getStatus().name() : null
            );
        }
    }

    private User validateDoctorOwnership(Encounter encounter, Long doctorUserId) {
        User doctorUser = userRepository.findById(doctorUserId)
                                        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        if (doctorUser.getDoctorProfile() == null || !doctorUser.getDoctorProfile().getId().equals(encounter.getDoctor().getId())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Bạn không thuộc bác sĩ phụ trách lần khám này");
        }

        return doctorUser;
    }

    private void ensureMedicationActive(Medication medication) {
        if (medication.getStatus() != MedicationStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Thuốc đang ngừng sử dụng");
        }
    }

    private void validateMedicationSafety(
            Medication medication,
            List<PatientAllergy> patientAllergies,
            List<PrescriptionItem> existingItems
    ) {
        validatePatientAllergies(medication, patientAllergies);
        validateDrugInteractions(medication, existingItems);
    }

    private void validatePatientAllergies(Medication medication, List<PatientAllergy> patientAllergies) {
        for (PatientAllergy allergy : patientAllergies) {
            String allergenName = StringUtil.trimToNull(allergy.getAllergenName());
            if (allergenName == null) {
                continue;
            }

            boolean matchesMedicationName = medication.getName() != null
                    && allergenName.equalsIgnoreCase(medication.getName());
            boolean matchesGenericName = medication.getGenericName() != null
                    && medication.getGenericName().toLowerCase().contains(allergenName.toLowerCase());

            if (matchesMedicationName || matchesGenericName) {
                throw new ApiException(
                        ErrorCode.PRESCRIPTION_ALLERGY_WARNING,
                        "Cảnh báo an toàn: Bệnh nhân có tiền sử dị ứng với '" + allergy.getAllergenName()
                                + "' (Mức độ: " + allergy.getSeverity() + "). Không thể kê thuốc: " + medication.getName()
                );
            }
        }
    }

    private void validateDrugInteractions(Medication medication, List<PrescriptionItem> existingItems) {
        for (PrescriptionItem existingItem : existingItems) {
            if (existingItem.getMedication() == null || existingItem.getMedication().getId() == null) {
                continue;
            }

            List<DrugInteraction> interactions = drugInteractionRepository.findByMedicationPair(
                    medication.getId(),
                    existingItem.getMedication().getId()
            );

            for (DrugInteraction interaction : interactions) {
                if (isBlockingInteraction(interaction)) {
                    throw new ApiException(
                            ErrorCode.PRESCRIPTION_DRUG_INTERACTION,
                            "Tương tác thuốc nguy hiểm giữa '" + medication.getName() + "' và '"
                                    + existingItem.getMedicationNameSnapshot() + "': " + interaction.getDescription()
                                    + " (Mức độ: " + interaction.getSeverity() + ")"
                    );
                }
            }
        }
    }

    private boolean isBlockingInteraction(DrugInteraction interaction) {
        return interaction.getSeverity() == DrugInteraction.InteractionSeverity.SEVERE
                || interaction.getSeverity() == DrugInteraction.InteractionSeverity.CONTRAINDICATED;
    }

    private String generateCode(Encounter encounter) {
        return "RX"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
                + String.format("%06d", encounter.getId());
    }

    private PrescriptionResponse toResponse(Prescription p) {
        Encounter encounter = p.getEncounter();

        return PrescriptionResponse.builder()
                                   .id(p.getId())
                                   .code(p.getCode())
                                   .encounterId(encounter != null ? encounter.getId() : null)
                                   .encounterCode(encounter != null ? encounter.getCode() : null)
                                   .doctorUserId(p.getDoctorUser().getId())
                                   .issuedDate(p.getIssuedDate())
                                   .generalNote(p.getGeneralNote())
                                   .status(p.getStatus())
                                   .items(p.getItems().stream().map(i ->
                                           PrescriptionItemResponse.builder()
                                                                   .id(i.getId())
                                                                   .medicationId(i.getMedication().getId())
                                                                   .medicationCode(i.getMedicationCodeSnapshot())
                                                                   .medicationName(i.getMedicationNameSnapshot())
                                                                   .strength(i.getStrengthSnapshot())
                                                                   .dosageForm(i.getDosageFormSnapshot())
                                                                   .unit(i.getUnitSnapshot())
                                                                   .quantity(i.getQuantity())
                                                                   .dose(i.getDose())
                                                                   .frequency(i.getFrequency())
                                                                   .durationDays(i.getDurationDays())
                                                                   .route(i.getRoute())
                                                                   .instruction(i.getInstruction())
                                                                   .status(i.getStatus())
                                                                   .refundReason(i.getRefundReason())
                                                                   .refundedAt(i.getRefundedAt())
                                                                   .build()
                                   ).toList())
                                   .build();
    }

    private Map<String, Object> snapshotPrescription(Prescription prescription) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", prescription.getId());
        data.put("code", prescription.getCode());
        data.put("encounterId", prescription.getEncounter() != null ? prescription.getEncounter().getId() : null);
        data.put("doctorUserId", prescription.getDoctorUser() != null ? prescription.getDoctorUser().getId() : null);
        data.put("status", prescription.getStatus() != null ? prescription.getStatus().name() : null);
        data.put("issuedDate", prescription.getIssuedDate());
        data.put("generalNote", prescription.getGeneralNote());
        data.put("itemCount", prescription.getItems() != null ? prescription.getItems().size() : 0);
        return data;
    }
}
