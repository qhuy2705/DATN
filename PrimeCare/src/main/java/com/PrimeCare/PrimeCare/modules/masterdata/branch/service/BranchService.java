package com.PrimeCare.PrimeCare.modules.masterdata.branch.service;

import com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.request.CreateBranchRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.request.UpdateBranchRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.request.UpdateBranchStatusRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.response.BranchResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.mapper.BranchMapper;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.common.StatusSummaryResponse;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    @Transactional
    public BranchResponse create(CreateBranchRequest req) {
        String code = req.getCode().trim();

        if (branchRepository.existsByCode(code)) {
            throw new ApiException(ErrorCode.BRANCH_CODE_EXISTS);
        }

        Branch b = Branch.builder()
                .code(code)
                .nameVn(req.getNameVn().trim())
                .nameEn(req.getNameEn().trim())
                .addressVn(req.getAddressVn().trim())
                .addressEn(req.getAddressEn().trim())
                .phone(StringUtil.trimToNull(req.getPhone()))
                .email(StringUtil.trimToNull(req.getEmail()))
                .lat(toBigDecimal(req.getLatitude()))
                .lng(toBigDecimal(req.getLongitude()))
                .descriptionVn(StringUtil.trimToNull(req.getDescriptionVn()))
                .descriptionEn(StringUtil.trimToNull(req.getDescriptionEn()))
                .status(BranchStatus.ACTIVE)
                .build();

        b = branchRepository.save(b);
        return BranchMapper.toResponse(b);
    }

    @Transactional
    public BranchResponse update(Long id, UpdateBranchRequest req) {
        Branch b = branchRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));

        b.setNameVn(req.getNameVn().trim());
        b.setNameEn(req.getNameEn().trim());
        b.setAddressVn(req.getAddressVn().trim());
        b.setAddressEn(req.getAddressEn().trim());
        b.setPhone(StringUtil.trimToNull(req.getPhone()));
        b.setEmail(StringUtil.trimToNull(req.getEmail()));
        b.setLat(toBigDecimal(req.getLatitude()));
        b.setLng(toBigDecimal(req.getLongitude()));
        b.setDescriptionVn(StringUtil.trimToNull(req.getDescriptionVn()));
        b.setDescriptionEn(StringUtil.trimToNull(req.getDescriptionEn()));

        b = branchRepository.save(b);
        return BranchMapper.toResponse(b);
    }

    @Transactional
    public BranchResponse updateStatus(Long id, UpdateBranchStatusRequest req) {
        Branch b = branchRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));

        b.setStatus(req.getStatus());
        b = branchRepository.save(b);
        return BranchMapper.toResponse(b);
    }

    @Transactional(readOnly = true)
    public BranchResponse getById(Long id, boolean publicOnly) {
        Branch b = branchRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));

        if (publicOnly && b.getStatus() != BranchStatus.ACTIVE) {
            throw new ApiException(ErrorCode.BRANCH_NOT_FOUND);
        }

        return BranchMapper.toResponse(b);
    }

    @Transactional(readOnly = true)
    public PageResponse<BranchResponse> listPage(boolean publicOnly, String q, BranchStatus status, Pageable pageable){
        Page<Branch> page;

        if (publicOnly) {
            page = branchRepository.searchAdmin(q, BranchStatus.ACTIVE, pageable);
        } else {
            page = branchRepository.searchAdmin(
                    (q != null && !q.isBlank()) ? q.trim() : null,
                    status,
                    pageable
            );
        }

        var items = page.getContent().stream().map(BranchMapper::toResponse).toList();
        String sort = pageable.getSort().isEmpty() ? null : pageable.getSort().toString();

        return PageResponse.<BranchResponse>builder()
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
    public StatusSummaryResponse summary(String q) {
        long active = 0;
        long inactive = 0;
        String keyword = (q != null && !q.isBlank()) ? q.trim() : null;

        for (var row : branchRepository.countAdminSummary(keyword)) {
            if (row.getStatus() == BranchStatus.ACTIVE) {
                active = row.getCount();
            } else if (row.getStatus() == BranchStatus.INACTIVE) {
                inactive = row.getCount();
            }
        }

        return StatusSummaryResponse.builder()
                                    .total(active + inactive)
                                    .active(active)
                                    .inactive(inactive)
                                    .build();
    }

    private BigDecimal toBigDecimal(Double v) {
        return v == null ? null : BigDecimal.valueOf(v);
    }
}
