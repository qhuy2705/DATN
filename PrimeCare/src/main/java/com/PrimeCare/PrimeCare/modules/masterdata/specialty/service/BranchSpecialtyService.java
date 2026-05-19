package com.PrimeCare.PrimeCare.modules.masterdata.specialty.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request.CreateBranchSpecialtyRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request.UpdateBranchSpecialtyRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.response.BranchSpecialtyResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.BranchSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.BranchSpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BranchSpecialtyService {

    private final BranchSpecialtyRepository branchSpecialtyRepository;
    private final BranchRepository branchRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public BranchSpecialtyResponse create(CreateBranchSpecialtyRequest req) {
        if (branchSpecialtyRepository.existsByBranch_IdAndSpecialty_Id(req.getBranchId(), req.getSpecialtyId())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chi nhánh đã có chuyên khoa này");
        }

        Branch branch = branchRepository.findById(req.getBranchId())
                                        .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));

        Specialty specialty = specialtyRepository.findById(req.getSpecialtyId())
                                                 .orElseThrow(() -> new ApiException(ErrorCode.SPECIALTY_NOT_FOUND));

        BranchSpecialty entity = BranchSpecialty.builder()
                                                .branch(branch)
                                                .specialty(specialty)
                                                .status(BranchSpecialtyStatus.ACTIVE)
                                                .displayOrder(req.getDisplayOrder())
                                                .consultationFee(req.getConsultationFee())
                                                .slotMinutesOverride(req.getSlotMinutesOverride())
                                                .note(StringUtil.trimToNull(req.getNote()))
                                                .build();

        BranchSpecialty saved = branchSpecialtyRepository.save(entity);
        auditLogService.log(null, "ASSIGN_BRANCH_SPECIALTY", "BRANCH_SPECIALTY", saved.getId(), null, snapshotBranchSpecialty(saved));
        return toResponse(saved);
    }

    @Transactional
    public BranchSpecialtyResponse update(Long id, UpdateBranchSpecialtyRequest req) {
        BranchSpecialty entity = branchSpecialtyRepository.findById(id)
                                                          .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Không tìm thấy cấu hình chuyên khoa của chi nhánh"));
        Map<String, Object> before = snapshotBranchSpecialty(entity);

        entity.setStatus(req.getStatus());
        entity.setDisplayOrder(req.getDisplayOrder());
        entity.setConsultationFee(req.getConsultationFee());
        entity.setSlotMinutesOverride(req.getSlotMinutesOverride());
        entity.setNote(StringUtil.trimToNull(req.getNote()));

        BranchSpecialty saved = branchSpecialtyRepository.save(entity);
        String action = saved.getStatus() == BranchSpecialtyStatus.INACTIVE
                ? "UNASSIGN_BRANCH_SPECIALTY"
                : "UPDATE_BRANCH_SPECIALTY";
        auditLogService.log(null, action, "BRANCH_SPECIALTY", saved.getId(), before, snapshotBranchSpecialty(saved));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<BranchSpecialtyResponse> listAdmin(Long branchId, String q, BranchSpecialtyStatus status, Pageable pageable) {
        Page<BranchSpecialty> page = branchSpecialtyRepository.searchAdmin(
                branchId,
                (q != null && !q.isBlank()) ? q.trim() : null,
                status,
                pageable
        );

        return PageResponse.<BranchSpecialtyResponse>builder()
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

    @Transactional(readOnly = true)
    public List<BranchSpecialtyResponse> listPublicByBranch(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                                        .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));

        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chi nhánh tạm ngưng hoạt động");
        }

        return branchSpecialtyRepository.findActiveByBranchId(branchId)
                                        .stream()
                                        .map(this::toResponse)
                                        .toList();
    }

    @Transactional(readOnly = true)
    public void validateBranchSpecialtyActive(Long branchId, Long specialtyId) {
        getActiveBranchSpecialtyEntity(branchId, specialtyId);
    }

    @Transactional(readOnly = true)
    public int resolveSlotMinutes(Long branchId, Long specialtyId, int defaultSlotMinutes) {
        BranchSpecialty branchSpecialty = getActiveBranchSpecialtyEntity(branchId, specialtyId);
        Integer override = branchSpecialty.getSlotMinutesOverride();
        return override != null && override > 0 ? override : defaultSlotMinutes;
    }

    @Transactional(readOnly = true)
    public BranchSpecialty getActiveBranchSpecialtyEntity(Long branchId, Long specialtyId) {
        BranchSpecialty branchSpecialty = branchSpecialtyRepository
                .findByBranch_IdAndSpecialty_IdAndStatus(branchId, specialtyId, BranchSpecialtyStatus.ACTIVE)
                .orElseGet(() -> {
                    Branch branch = branchRepository.findById(branchId)
                                                    .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));
                    if (branch.getStatus() != BranchStatus.ACTIVE) {
                        throw new ApiException(ErrorCode.INVALID_REQUEST, "Chi nhánh tạm ngưng hoạt động");
                    }

                    Specialty specialty = specialtyRepository.findById(specialtyId)
                                                             .orElseThrow(() -> new ApiException(ErrorCode.SPECIALTY_NOT_FOUND));
                    if (!"ACTIVE".equalsIgnoreCase(specialty.getStatus())) {
                        throw new ApiException(ErrorCode.INVALID_REQUEST, "Chuyên khoa tạm ngưng hoạt động");
                    }

                    throw new ApiException(ErrorCode.INVALID_REQUEST, "Chuyên khoa tại chi nhánh đang tạm ngưng hoạt động");
                });

        if (branchSpecialty.getBranch().getStatus() != BranchStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chi nhánh tạm ngưng hoạt động");
        }
        if (!"ACTIVE".equalsIgnoreCase(branchSpecialty.getSpecialty().getStatus())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chuyên khoa tạm ngưng hoạt động");
        }

        return branchSpecialty;
    }

    private BranchSpecialtyResponse toResponse(BranchSpecialty entity) {
        boolean selfActive = entity.getStatus() == BranchSpecialtyStatus.ACTIVE;
        boolean branchActive = entity.getBranch().getStatus() == BranchStatus.ACTIVE;
        boolean specialtyActive = "ACTIVE".equalsIgnoreCase(entity.getSpecialty().getStatus());

        String effectiveStatus = "ACTIVE";
        String inactiveReason = null;
        boolean bookable = true;

        if (!selfActive) {
            effectiveStatus = "INACTIVE";
            inactiveReason = "SELF_INACTIVE";
            bookable = false;
        } else if (!branchActive) {
            effectiveStatus = "INACTIVE";
            inactiveReason = "BRANCH_INACTIVE";
            bookable = false;
        } else if (!specialtyActive) {
            effectiveStatus = "INACTIVE";
            inactiveReason = "SPECIALTY_INACTIVE";
            bookable = false;
        }

        return BranchSpecialtyResponse.builder()
                                      .id(entity.getId())
                                      .branchId(entity.getBranch().getId())
                                      .branchName(entity.getBranch().getNameVn())
                                      .specialtyId(entity.getSpecialty().getId())
                                      .specialtyCode(entity.getSpecialty().getCode())
                                      .specialtyNameVn(entity.getSpecialty().getNameVn())
                                      .specialtyNameEn(entity.getSpecialty().getNameEn())
                                      .specialtyDescriptionVn(entity.getSpecialty().getDescriptionVn())
                                      .specialtyDescriptionEn(entity.getSpecialty().getDescriptionEn())
                                      .iconUrl(entity.getSpecialty().getIconUrl())
                                      .status(entity.getStatus())
                                      .effectiveStatus(effectiveStatus)
                                      .inactiveReason(inactiveReason)
                                      .bookable(bookable)
                                      .displayOrder(entity.getDisplayOrder())
                                      .consultationFee(entity.getConsultationFee())
                                      .slotMinutesOverride(entity.getSlotMinutesOverride())
                                      .note(entity.getNote())
                                      .build();
    }

    private Map<String, Object> snapshotBranchSpecialty(BranchSpecialty entity) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", entity.getId());
        data.put("branchId", entity.getBranch() != null ? entity.getBranch().getId() : null);
        data.put("specialtyId", entity.getSpecialty() != null ? entity.getSpecialty().getId() : null);
        data.put("status", entity.getStatus() != null ? entity.getStatus().name() : null);
        data.put("displayOrder", entity.getDisplayOrder());
        data.put("consultationFee", entity.getConsultationFee());
        data.put("slotMinutesOverride", entity.getSlotMinutesOverride());
        data.put("note", entity.getNote());
        return data;
    }
}
