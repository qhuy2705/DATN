package com.PrimeCare.PrimeCare.modules.staff.service;

import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.auth.service.AccountProvisionService;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.staff.dto.request.CreateStaffProfileRequest;
import com.PrimeCare.PrimeCare.modules.staff.dto.request.UpdateStaffProfileRequest;
import com.PrimeCare.PrimeCare.modules.staff.dto.request.UpdateStaffStatusRequest;
import com.PrimeCare.PrimeCare.modules.staff.dto.response.StaffAdminSummaryResponse;
import com.PrimeCare.PrimeCare.modules.staff.dto.response.StaffProfileResponse;
import com.PrimeCare.PrimeCare.modules.staff.entity.StaffProfile;
import com.PrimeCare.PrimeCare.modules.staff.mapper.StaffProfileMapper;
import com.PrimeCare.PrimeCare.modules.staff.repository.StaffProfileRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.StaffStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
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
public class StaffAdminService {

    private final StaffProfileRepository staffProfileRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final AccountProvisionService accountProvisionService;
    private final AuditLogService auditLogService;

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

        auditLogService.log(null, "CREATE_STAFF", "STAFF", s.getId(), null, snapshotStaff(s));

        return mapResponse(s);
    }

    @Transactional
    public StaffProfileResponse update(Long id, UpdateStaffProfileRequest req) {
        StaffProfile s = staffProfileRepository.findById(id)
                                               .orElseThrow(() -> new ApiException(ErrorCode.STAFF_PROFILE_NOT_FOUND));
        Map<String, Object> before = snapshotStaff(s);

        Branch branch = branchRepository.findById(req.getBranchId())
                                        .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));

        s.setFullName(req.getFullName().trim());
        s.setBranch(branch);

        s = staffProfileRepository.save(s);

        auditLogService.log(null, "UPDATE_STAFF", "STAFF", s.getId(), before, snapshotStaff(s));

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
        return list(branchId, q, status, null, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffProfileResponse> list(Long branchId,
                                                   String q,
                                                   StaffStatus status,
                                                   UserRole role,
                                                   Pageable pageable) {
        Page<StaffProfile> page = staffProfileRepository.searchAdmin(
                branchId,
                (q != null && !q.isBlank()) ? q.trim() : null,
                status,
                role,
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
        Map<String, Object> before = snapshotStaff(s);

        s.setStatus(req.getStatus());
        s = staffProfileRepository.save(s);
        accountProvisionService.syncStaffAccountStatus(s.getId(), req.getStatus());

        auditLogService.log(null, "UPDATE_STAFF_STATUS", "STAFF", s.getId(), before, snapshotStaff(s));

        return mapResponse(s);
    }

    @Transactional(readOnly = true)
    public StaffAdminSummaryResponse summary(Long branchId, String q, StaffStatus status) {
        return summary(branchId, q, status, null);
    }

    @Transactional(readOnly = true)
    public StaffAdminSummaryResponse summary(Long branchId, String q, StaffStatus status, UserRole role) {
        String keyword = (q != null && !q.isBlank()) ? q.trim() : null;
        var row = staffProfileRepository.summarizeAdmin(branchId, keyword, status, role);

        return StaffAdminSummaryResponse.builder()
                                        .total(row != null ? row.getTotal() : 0)
                                        .active(row != null ? row.getActive() : 0)
                                        .inactive(row != null ? row.getInactive() : 0)
                                        .noAccountStaffs(row != null ? row.getNoAccountStaffs() : 0)
                                        .inactiveAccountStaffs(row != null ? row.getInactiveAccountStaffs() : 0)
                                        .build();
    }

    public StaffAdminSummaryResponse summary(Long branchId, String q) {
        return summary(branchId, q, null);
    }

    private StaffProfileResponse mapResponse(StaffProfile staff) {
        return StaffProfileMapper.toResponse(staff, userRepository.findByStaffProfile_Id(staff.getId()).orElse(null));
    }

    private Map<String, Object> snapshotStaff(StaffProfile staff) {
        var user = staff.getId() != null ? userRepository.findByStaffProfile_Id(staff.getId()).orElse(null) : null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", staff.getId());
        data.put("fullName", staff.getFullName());
        data.put("email", user != null ? user.getEmail() : null);
        data.put("role", user != null && user.getRole() != null ? user.getRole().name() : null);
        data.put("status", staff.getStatus() != null ? staff.getStatus().name() : null);
        data.put("branchId", staff.getBranch() != null ? staff.getBranch().getId() : null);
        return data;
    }
}
