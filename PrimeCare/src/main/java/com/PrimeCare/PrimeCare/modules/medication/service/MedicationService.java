package com.PrimeCare.PrimeCare.modules.medication.service;

import com.PrimeCare.PrimeCare.modules.medication.dto.request.CreateMedicationRequest;
import com.PrimeCare.PrimeCare.modules.medication.dto.request.UpdateMedicationRequest;
import com.PrimeCare.PrimeCare.modules.medication.dto.response.MedicationResponse;
import com.PrimeCare.PrimeCare.modules.medication.dto.request.UpdateMedicationStatusRequest;
import com.PrimeCare.PrimeCare.modules.medication.entity.Medication;
import com.PrimeCare.PrimeCare.modules.medication.repository.MedicationRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.MedicationStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MedicationService {

    private final MedicationRepository medicationRepository;

    @Transactional
    public MedicationResponse create(CreateMedicationRequest req) {
        if (medicationRepository.existsByCode(req.getCode().trim())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Mã thuốc đã tồn tại");
        }

        Medication entity = Medication.builder()
                                      .code(req.getCode().trim())
                                      .name(req.getName().trim())
                                      .genericName(StringUtil.trimToNull(req.getGenericName()))
                                      .strength(StringUtil.trimToNull(req.getStrength()))
                                      .dosageForm(StringUtil.trimToNull(req.getDosageForm()))
                                      .unit(StringUtil.trimToNull(req.getUnit()))
                                      .manufacturer(StringUtil.trimToNull(req.getManufacturer()))
                                      .indicationNote(StringUtil.trimToNull(req.getIndicationNote()))
                                      .contraindicationNote(StringUtil.trimToNull(req.getContraindicationNote()))
                                      .status(MedicationStatus.ACTIVE)
                                      .build();

        return toResponse(medicationRepository.save(entity));
    }

    @Transactional
    public MedicationResponse update(Long id, UpdateMedicationRequest req) {
        Medication entity = medicationRepository.findById(id)
                                                .orElseThrow(() -> new ApiException(ErrorCode.MEDICATION_NOT_FOUND));

        if (req.getName() != null && !req.getName().isBlank()) entity.setName(req.getName().trim());
        if (req.getGenericName() != null) entity.setGenericName(StringUtil.trimToNull(req.getGenericName()));
        if (req.getStrength() != null) entity.setStrength(StringUtil.trimToNull(req.getStrength()));
        if (req.getDosageForm() != null) entity.setDosageForm(StringUtil.trimToNull(req.getDosageForm()));
        if (req.getUnit() != null) entity.setUnit(StringUtil.trimToNull(req.getUnit()));
        if (req.getManufacturer() != null) entity.setManufacturer(StringUtil.trimToNull(req.getManufacturer()));
        if (req.getIndicationNote() != null) entity.setIndicationNote(StringUtil.trimToNull(req.getIndicationNote()));
        if (req.getContraindicationNote() != null)
            entity.setContraindicationNote(StringUtil.trimToNull(req.getContraindicationNote()));

        return toResponse(medicationRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public PageResponse<MedicationResponse> listActive(String q, Pageable pageable) {
        Page<Medication> page = medicationRepository.searchAdmin(
                (q == null || q.isBlank()) ? null : q.trim(),
                MedicationStatus.ACTIVE,
                pageable
        );

        return toPageResponse(page, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<MedicationResponse> listAdmin(String q, MedicationStatus status, Pageable pageable) {
        Page<Medication> page = medicationRepository.searchAdmin(
                (q == null || q.isBlank()) ? null : q.trim(),
                status,
                pageable
        );

        return toPageResponse(page, pageable);
    }

    private PageResponse<MedicationResponse> toPageResponse(Page<Medication> page, Pageable pageable) {
        var items = page.getContent().stream()
                        .map(this::toResponse)
                        .toList();

        String sort = pageable.getSort().isEmpty() ? null : pageable.getSort().toString();

        return PageResponse.<MedicationResponse>builder()
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

    @Transactional
    public MedicationResponse updateStatus(Long id, UpdateMedicationStatusRequest req) {
        Medication entity = medicationRepository.findById(id)
                                                .orElseThrow(() -> new ApiException(ErrorCode.MEDICATION_NOT_FOUND));

        entity.setStatus(req.getStatus());
        return toResponse(medicationRepository.save(entity));
    }

    private MedicationResponse toResponse(Medication e) {
        return MedicationResponse.builder()
                                 .id(e.getId())
                                 .code(e.getCode())
                                 .name(e.getName())
                                 .genericName(e.getGenericName())
                                 .strength(e.getStrength())
                                 .dosageForm(e.getDosageForm())
                                 .unit(e.getUnit())
                                 .manufacturer(e.getManufacturer())
                                 .indicationNote(e.getIndicationNote())
                                 .contraindicationNote(e.getContraindicationNote())
                                 .status(e.getStatus())
                                 .build();
    }
}
