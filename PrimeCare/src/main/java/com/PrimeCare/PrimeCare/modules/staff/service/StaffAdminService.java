package com.PrimeCare.PrimeCare.modules.staff.service;

import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.auth.service.AccountProvisionService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.staff.dto.request.CreateStaffProfileRequest;
import com.PrimeCare.PrimeCare.modules.staff.dto.request.UpdateStaffProfileRequest;
import com.PrimeCare.PrimeCare.modules.staff.dto.request.UpdateStaffStatusRequest;
import com.PrimeCare.PrimeCare.modules.staff.dto.response.StaffProfileResponse;
import com.PrimeCare.PrimeCare.modules.staff.entity.StaffProfile;
import com.PrimeCare.PrimeCare.modules.staff.mapper.StaffProfileMapper;
import com.PrimeCare.PrimeCare.modules.staff.repository.StaffProfileRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.common.StatusSummaryResponse;
import com.PrimeCare.PrimeCare.shared.enums.StaffStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StaffAdminService {

    private final StaffProfileRepository staffProfileRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final AccountProvisionService accountProvisionService;

    @Transactional
    public StaffProfileResponse create(CreateStaffProfileRequest req) {
        Branch branch = branchRepository.findById(req.getBranchId())
                                        .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));

        StaffProfile s = StaffProfile.builder()
                                     .fullName(req.getFullName().trim())
                                     .branch(branch)
                                     .status(StaffStatus.ACTIVE)
                                     .build();

        s = staffProfileRepository.save(s);

        return mapResponse(s);
    }

    @Transactional
    public StaffProfileResponse update(Long id, UpdateStaffProfileRequest req) {
        StaffProfile s = staffProfileRepository.findById(id)
                                               .orElseThrow(() -> new ApiException(ErrorCode.STAFF_PROFILE_NOT_FOUND));

        Branch branch = branchRepository.findById(req.getBranchId())
                                        .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));

        s.setFullName(req.getFullName().trim());
        s.setBranch(branch);

        s = staffProfileRepository.save(s);

        return mapResponse(s);
    }

    @Transactional(readOnly = true)
    public StaffProfileResponse get(Long id) {
        StaffProfile s = staffProfileRepository.findById(id)
                                               .orElseThrow(() -> new ApiException(ErrorCode.STAFF_PROFILE_NOT_FOUND));

        return mapResponse(s);
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffProfileResponse> list(Long branchId,
                                                   String q,
                                                   StaffStatus status,
                                                   Pageable pageable) {
        Page<StaffProfile> page = staffProfileRepository.searchAdmin(
                branchId,
                (q != null && !q.isBlank()) ? q.trim() : null,
                status,
                pageable
        );

        var items = page.getContent().stream()
                        .map(this::mapResponse)
                        .toList();

        String sort = pageable.getSort().isEmpty() ? null : pageable.getSort().toString();
        return PageResponse.<StaffProfileResponse>builder()
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
    public StaffProfileResponse updateStatus(Long id, UpdateStaffStatusRequest req) {
        StaffProfile s = staffProfileRepository.findById(id)
                                               .orElseThrow(() -> new ApiException(ErrorCode.STAFF_PROFILE_NOT_FOUND));

        s.setStatus(req.getStatus());
        s = staffProfileRepository.save(s);
        accountProvisionService.syncStaffAccountStatus(s.getId(), req.getStatus());

        return mapResponse(s);
    }

    @Transactional(readOnly = true)
    public StatusSummaryResponse summary(Long branchId, String q) {
        long active = 0;
        long inactive = 0;
        String keyword = (q != null && !q.isBlank()) ? q.trim() : null;

        for (var row : staffProfileRepository.countAdminSummary(branchId, keyword)) {
            if (row.getStatus() == StaffStatus.ACTIVE) {
                active = row.getCount();
            } else if (row.getStatus() == StaffStatus.INACTIVE) {
                inactive = row.getCount();
            }
        }

        return StatusSummaryResponse.builder()
                                    .total(active + inactive)
                                    .active(active)
                                    .inactive(inactive)
                                    .build();
    }

    private StaffProfileResponse mapResponse(StaffProfile staff) {
        return StaffProfileMapper.toResponse(staff, userRepository.findByStaffProfile_Id(staff.getId()).orElse(null));
    }

}
