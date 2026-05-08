package com.PrimeCare.PrimeCare.modules.medical_service.service;

import com.PrimeCare.PrimeCare.modules.medical_service.dto.request.CreateMedicalServiceRequest;
import com.PrimeCare.PrimeCare.modules.medical_service.dto.request.UpdateMedicalServiceRequest;
import com.PrimeCare.PrimeCare.modules.medical_service.dto.response.MedicalServiceResponse;
import com.PrimeCare.PrimeCare.modules.medical_service.dto.response.PublicMedicalServiceCatalogResponse;
import com.PrimeCare.PrimeCare.modules.medical_service.entity.MedicalService;
import com.PrimeCare.PrimeCare.modules.medical_service.repository.MedicalServiceRepository;
import com.PrimeCare.PrimeCare.modules.service_result.support.ServiceResultTemplateSupport;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MedicalServiceService {

    private final MedicalServiceRepository repository;

    @Transactional
    public MedicalServiceResponse create(CreateMedicalServiceRequest req) {
        if (repository.existsByCode(req.getCode())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Mã dịch vụ đã tồn tại");
        }

        var templateCode = ServiceResultTemplateSupport.resolveTemplateCode(
                req.getResultTemplateCode(),
                req.getServiceType(),
                req.getRequiresNumericResult(),
                req.getRequiresFileResult()
        );
        String templateSchemaJson = StringUtil.trimToNull(req.getResultTemplateSchemaJson());
        if (templateSchemaJson == null) {
            templateSchemaJson = ServiceResultTemplateSupport.defaultSchemaJson(templateCode);
        }
        String resultReportTitle = ServiceResultTemplateSupport.resolveReportTitle(
                StringUtil.trimToNull(req.getResultReportTitle()),
                req.getNameVn()
        );

        MedicalService e = MedicalService.builder()
                                         .code(req.getCode())
                                         .nameVn(req.getNameVn())
                                         .nameEn(req.getNameEn())
                                         .descriptionVn(req.getDescriptionVn())
                                         .descriptionEn(req.getDescriptionEn())
                                         .serviceType(req.getServiceType())
                                         .departmentCode(normalizeDepartmentCode(req.getDepartmentCode()))
                                         .basePrice(req.getBasePrice())
                                         .publicVisible(req.getPublicVisible())
                                         .displayOrder(req.getDisplayOrder())
                                         .thumbnailUrl(req.getThumbnailUrl())
                                         .defaultTurnaroundMinutes(req.getDefaultTurnaroundMinutes())
                                         .requiresFileResult(req.getRequiresFileResult())
                                         .requiresNumericResult(req.getRequiresNumericResult())
                                         .resultTemplateCode(templateCode)
                                         .resultTemplateSchemaJson(templateSchemaJson)
                                         .resultReportTitle(resultReportTitle)
                                         .build();

        return toResponse(repository.save(e));
    }

    @Transactional
    public MedicalServiceResponse update(Long id, UpdateMedicalServiceRequest req) {
        MedicalService e = repository.findById(id)
                                     .orElseThrow(() -> new ApiException(ErrorCode.MEDICAL_SERVICE_NOT_FOUND));

        if (req.getNameVn() != null) e.setNameVn(req.getNameVn());
        if (req.getNameEn() != null) e.setNameEn(req.getNameEn());
        if (req.getDescriptionVn() != null) e.setDescriptionVn(req.getDescriptionVn());
        if (req.getDescriptionEn() != null) e.setDescriptionEn(req.getDescriptionEn());
        if (req.getServiceType() != null) e.setServiceType(req.getServiceType());
        if (req.getDepartmentCode() != null) e.setDepartmentCode(normalizeDepartmentCode(req.getDepartmentCode()));
        if (req.getBasePrice() != null) e.setBasePrice(req.getBasePrice());
        if (req.getStatus() != null) e.setStatus(req.getStatus());
        if (req.getPublicVisible() != null) e.setPublicVisible(req.getPublicVisible());
        if (req.getDisplayOrder() != null) e.setDisplayOrder(req.getDisplayOrder());
        if (req.getThumbnailUrl() != null) e.setThumbnailUrl(req.getThumbnailUrl());
        if (req.getDefaultTurnaroundMinutes() != null) e.setDefaultTurnaroundMinutes(req.getDefaultTurnaroundMinutes());
        if (req.getRequiresFileResult() != null) e.setRequiresFileResult(req.getRequiresFileResult());
        if (req.getRequiresNumericResult() != null) e.setRequiresNumericResult(req.getRequiresNumericResult());
        if (req.getResultTemplateCode() != null) {
            e.setResultTemplateCode(req.getResultTemplateCode());
            if (req.getResultTemplateSchemaJson() == null) {
                e.setResultTemplateSchemaJson(ServiceResultTemplateSupport.defaultSchemaJson(req.getResultTemplateCode()));
            }
        }
        if (req.getResultTemplateSchemaJson() != null) {
            String schemaJson = StringUtil.trimToNull(req.getResultTemplateSchemaJson());
            e.setResultTemplateSchemaJson(schemaJson != null
                    ? schemaJson
                    : ServiceResultTemplateSupport.defaultSchemaJson(e.getResultTemplateCode()));
        }
        if (req.getResultReportTitle() != null) {
            e.setResultReportTitle(ServiceResultTemplateSupport.resolveReportTitle(req.getResultReportTitle(), e.getNameVn()));
        }

        if (e.getResultTemplateCode() == null) {
            e.setResultTemplateCode(ServiceResultTemplateSupport.resolveTemplateCode(e));
        }
        if (StringUtil.isBlank(e.getResultTemplateSchemaJson())) {
            e.setResultTemplateSchemaJson(ServiceResultTemplateSupport.defaultSchemaJson(e.getResultTemplateCode()));
        }
        if (StringUtil.isBlank(e.getResultReportTitle())) {
            e.setResultReportTitle(ServiceResultTemplateSupport.resolveReportTitle(e));
        }

        return toResponse(repository.save(e));
    }

    @Transactional(readOnly = true)
    public List<PublicMedicalServiceCatalogResponse> listPublicCatalog() {
        return repository.findByPublicVisibleTrueAndStatusOrderByDisplayOrderAscNameVnAsc(MedicalServiceStatus.ACTIVE)
                         .stream()
                         .map(this::toPublicCatalogResponse)
                         .toList();
    }

    @Transactional(readOnly = true)
    public PublicMedicalServiceCatalogResponse getPublicCatalogItem(Long id) {
        MedicalService service = repository.findById(id)
                .filter(item -> item.getStatus() == MedicalServiceStatus.ACTIVE && Boolean.TRUE.equals(item.getPublicVisible()))
                .orElseThrow(() -> new ApiException(ErrorCode.MEDICAL_SERVICE_NOT_FOUND));
        return toPublicCatalogResponse(service);
    }

    @Transactional
    public MedicalServiceResponse updateStatus(Long id, MedicalServiceStatus status) {
        MedicalService service = repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDICAL_SERVICE_NOT_FOUND));
        service.setStatus(status);
        return toResponse(repository.save(service));
    }

    @Transactional(readOnly = true)
    public PageResponse<MedicalServiceResponse> listAdmin(
            String q,
            MedicalServiceStatus status,
            Boolean publicVisible,
            Pageable pageable
    ) {
        Page<MedicalService> page = repository.searchAdmin(
                (q != null && !q.isBlank()) ? q.trim() : null,
                status,
                publicVisible,
                pageable
        );

        return PageResponse.<MedicalServiceResponse>builder()
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

    private PublicMedicalServiceCatalogResponse toPublicCatalogResponse(MedicalService e) {
        return PublicMedicalServiceCatalogResponse.builder()
                                     .id(e.getId())
                                     .code(e.getCode())
                                     .nameVn(e.getNameVn())
                                     .nameEn(e.getNameEn())
                                     .descriptionVn(e.getDescriptionVn())
                                     .descriptionEn(e.getDescriptionEn())
                                     .serviceType(e.getServiceType())
                                     .basePrice(e.getBasePrice())
                                     .displayOrder(e.getDisplayOrder())
                                     .thumbnailUrl(e.getThumbnailUrl())
                                     .defaultTurnaroundMinutes(e.getDefaultTurnaroundMinutes())
                                     .build();
    }

    private MedicalServiceResponse toResponse(MedicalService e) {
        return MedicalServiceResponse.builder()
                                     .id(e.getId())
                                     .code(e.getCode())
                                     .nameVn(e.getNameVn())
                                     .nameEn(e.getNameEn())
                                     .descriptionVn(e.getDescriptionVn())
                                     .descriptionEn(e.getDescriptionEn())
                                     .serviceType(e.getServiceType())
                                     .departmentCode(e.getDepartmentCode())
                                     .basePrice(e.getBasePrice())
                                     .status(e.getStatus())
                                     .publicVisible(e.getPublicVisible())
                                     .displayOrder(e.getDisplayOrder())
                                     .thumbnailUrl(e.getThumbnailUrl())
                                     .defaultTurnaroundMinutes(e.getDefaultTurnaroundMinutes())
                                     .requiresFileResult(e.getRequiresFileResult())
                                     .requiresNumericResult(e.getRequiresNumericResult())
                                     .resultTemplateCode(e.getResultTemplateCode())
                                     .resultTemplateSchemaJson(e.getResultTemplateSchemaJson())
                                     .resultReportTitle(e.getResultReportTitle())
                                     .build();
    }

    private String normalizeDepartmentCode(String departmentCode) {
        String trimmed = StringUtil.trimToNull(departmentCode);
        return trimmed != null ? trimmed.toUpperCase(Locale.ROOT) : null;
    }
}
