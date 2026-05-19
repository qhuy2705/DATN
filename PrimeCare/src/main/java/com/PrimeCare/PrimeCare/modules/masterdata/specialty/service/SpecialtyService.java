package com.PrimeCare.PrimeCare.modules.masterdata.specialty.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request.CreateSpecialtyRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request.UpdateSpecialtyRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.response.SpecialtyResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SpecialtyService {

    private final SpecialtyRepository specialtyRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<SpecialtyResponse> listActive(String q, Pageable pageable) {
        Page<Specialty> page = specialtyRepository.searchAdmin(
                (q != null && !q.isBlank()) ? q.trim() : null,
                "ACTIVE",
                pageable
        );

        return toPageResponse(page, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<SpecialtyResponse> listAdmin(String q, String status, Pageable pageable) {
        Page<Specialty> page = specialtyRepository.searchAdmin(
                (q != null && !q.isBlank()) ? q.trim() : null,
                (status != null && !status.isBlank()) ? status.trim() : null,
                pageable
        );
        return toPageResponse(page, pageable);
    }

    @Transactional
    public SpecialtyResponse create(CreateSpecialtyRequest request) {
        if (specialtyRepository.existsByCode(request.getCode().trim())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Mã chuyên khoa đã tồn tại");
        }

        Specialty specialty = Specialty.builder()
                                       .code(request.getCode().trim())
                                       .nameVn(request.getNameVn().trim())
                                       .nameEn(request.getNameEn())
                                       .descriptionVn(request.getDescriptionVn())
                                       .descriptionEn(request.getDescriptionEn())
                                       .iconUrl(request.getIconUrl())
                                       .status("ACTIVE")
                                       .defaultSlotMinutes(request.getDefaultSlotMinutes())
                                       .maxPerSession(request.getMaxPerSession())
                                       .build();

        specialty = specialtyRepository.save(specialty);
        auditLogService.log(null, "CREATE_SPECIALTY", "SPECIALTY", specialty.getId(), null, snapshotSpecialty(specialty));
        return toResponse(specialty);
    }

    @Transactional
    public SpecialtyResponse update(Long id, UpdateSpecialtyRequest request) {
        Specialty specialty = specialtyRepository.findById(id)
                                                 .orElseThrow(() -> new ApiException(ErrorCode.SPECIALTY_NOT_FOUND));
        Map<String, Object> before = snapshotSpecialty(specialty);

        if (request.getNameVn() != null) specialty.setNameVn(request.getNameVn());
        if (request.getNameEn() != null) specialty.setNameEn(request.getNameEn());
        if (request.getDescriptionVn() != null) specialty.setDescriptionVn(request.getDescriptionVn());
        if (request.getDescriptionEn() != null) specialty.setDescriptionEn(request.getDescriptionEn());
        if (request.getIconUrl() != null) specialty.setIconUrl(request.getIconUrl());
        if (request.getStatus() != null) specialty.setStatus(request.getStatus());
        if (request.getDefaultSlotMinutes() != null) specialty.setDefaultSlotMinutes(request.getDefaultSlotMinutes());
        if (request.getMaxPerSession() != null) specialty.setMaxPerSession(request.getMaxPerSession());

        specialty = specialtyRepository.save(specialty);
        auditLogService.log(null, "UPDATE_SPECIALTY", "SPECIALTY", specialty.getId(), before, snapshotSpecialty(specialty));
        return toResponse(specialty);
    }

    @Transactional
    public SpecialtyResponse updateStatus(Long id, String status) {
        Specialty specialty = specialtyRepository.findById(id)
                                                 .orElseThrow(() -> new ApiException(ErrorCode.SPECIALTY_NOT_FOUND));
        Map<String, Object> before = snapshotSpecialty(specialty);

        specialty.setStatus(status.trim());
        specialty = specialtyRepository.save(specialty);
        auditLogService.log(null, "UPDATE_SPECIALTY_STATUS", "SPECIALTY", specialty.getId(), before, snapshotSpecialty(specialty));
        return toResponse(specialty);
    }

    @Transactional(readOnly = true)
    public SpecialtyResponse getActiveById(Long id) {
        Specialty specialty = specialtyRepository.findById(id)
                                                 .filter(it -> "ACTIVE".equalsIgnoreCase(it.getStatus()))
                                                 .orElseThrow(() -> new ApiException(ErrorCode.SPECIALTY_NOT_FOUND));

        return toResponse(specialty);
    }

    private PageResponse<SpecialtyResponse> toPageResponse(Page<Specialty> page, Pageable pageable) {
        return PageResponse.<SpecialtyResponse>builder()
                           .items(page.getContent().stream().map(this::toResponse).toList())
                           .meta(PageResponse.Meta.builder()
                                                  .page(page.getNumber())
                                                  .size(page.getSize())
                                                  .totalItems(page.getTotalElements())
                                                  .totalPages(page.getTotalPages())
                                                  .hasNext(page.hasNext())
                                                  .hasPrev(page.hasPrevious())
                                                  .sort(pageable.getSort().toString())
                                                  .build())
                           .build();
    }

    private SpecialtyResponse toResponse(Specialty entity) {
        return SpecialtyResponse.builder()
                                .id(entity.getId())
                                .code(entity.getCode())
                                .nameVn(entity.getNameVn())
                                .nameEn(entity.getNameEn())
                                .descriptionVn(entity.getDescriptionVn())
                                .descriptionEn(entity.getDescriptionEn())
                                .iconUrl(entity.getIconUrl())
                                .status(entity.getStatus())
                                .defaultSlotMinutes(entity.getDefaultSlotMinutes())
                                .maxPerSession(entity.getMaxPerSession())
                                .build();
    }

    private Map<String, Object> snapshotSpecialty(Specialty specialty) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", specialty.getId());
        data.put("code", specialty.getCode());
        data.put("nameVn", specialty.getNameVn());
        data.put("nameEn", specialty.getNameEn());
        data.put("status", specialty.getStatus());
        data.put("defaultSlotMinutes", specialty.getDefaultSlotMinutes());
        data.put("maxPerSession", specialty.getMaxPerSession());
        return data;
    }
}
