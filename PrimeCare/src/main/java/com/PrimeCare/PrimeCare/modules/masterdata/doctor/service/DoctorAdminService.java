package com.PrimeCare.PrimeCare.modules.masterdata.doctor.service;

import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.auth.service.AccountProvisionService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.CreateDoctorProfileRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.UpdateDoctorProfileRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.UpdateDoctorStatusRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response.DoctorProfileResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorSpecialtyId;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.mapper.DoctorProfileMapper;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.common.StatusSummaryResponse;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorAdminService {

    private final DoctorProfileRepository doctorProfileRepository;
    private final BranchRepository branchRepository;
    private final SpecialtyRepository specialtyRepository;
    private final UserRepository userRepository;
    private final AccountProvisionService accountProvisionService;

    @Transactional
    public DoctorProfileResponse create(CreateDoctorProfileRequest req) {
        Branch branch = branchRepository.findById(req.getBranchId())
                                        .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));

        DoctorProfile d = DoctorProfile.builder()
                                       .fullName(req.getFullName().trim())
                                       .branch(branch)
                                       .displayTitleVn(StringUtil.trimToNull(req.getDisplayTitleVn()))
                                       .displayTitleEn(StringUtil.trimToNull(req.getDisplayTitleEn()))
                                       .bioVn(StringUtil.trimToNull(req.getBioVn()))
                                       .bioEn(StringUtil.trimToNull(req.getBioEn()))
                                       .expertiseVn(StringUtil.trimToNull(req.getExpertiseVn()))
                                       .expertiseEn(StringUtil.trimToNull(req.getExpertiseEn()))
                                       .educationVn(StringUtil.trimToNull(req.getEducationVn()))
                                       .educationEn(StringUtil.trimToNull(req.getEducationEn()))
                                       .achievementsVn(StringUtil.trimToNull(req.getAchievementsVn()))
                                       .achievementsEn(StringUtil.trimToNull(req.getAchievementsEn()))
                                       .yearsExp(req.getYearsExp())
                                       .avatarUrl(StringUtil.trimToNull(req.getAvatarUrl()))
                                       .slotMinutesOverride(req.getSlotMinutesOverride())
                                       .status(req.getStatus() != null ? req.getStatus() : DoctorStatus.ACTIVE)
                                       .doctorSpecialties(new HashSet<>())
                                       .build();

        d = doctorProfileRepository.save(d);
        syncSpecialties(d, req.getSpecialtyIds());
        d = doctorProfileRepository.save(d);

        return mapResponse(d);
    }

    @Transactional
    public DoctorProfileResponse update(Long id, UpdateDoctorProfileRequest req) {
        DoctorProfile d = doctorProfileRepository.findById(id)
                                                 .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_PROFILE_NOT_FOUND));

        Branch branch = branchRepository.findById(req.getBranchId())
                                        .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));

        d.setFullName(req.getFullName().trim());
        d.setBranch(branch);
        d.setDisplayTitleVn(StringUtil.trimToNull(req.getDisplayTitleVn()));
        d.setDisplayTitleEn(StringUtil.trimToNull(req.getDisplayTitleEn()));
        d.setBioVn(StringUtil.trimToNull(req.getBioVn()));
        d.setBioEn(StringUtil.trimToNull(req.getBioEn()));
        d.setExpertiseVn(StringUtil.trimToNull(req.getExpertiseVn()));
        d.setExpertiseEn(StringUtil.trimToNull(req.getExpertiseEn()));
        d.setEducationVn(StringUtil.trimToNull(req.getEducationVn()));
        d.setEducationEn(StringUtil.trimToNull(req.getEducationEn()));
        d.setAchievementsVn(StringUtil.trimToNull(req.getAchievementsVn()));
        d.setAchievementsEn(StringUtil.trimToNull(req.getAchievementsEn()));
        d.setYearsExp(req.getYearsExp());
        d.setAvatarUrl(StringUtil.trimToNull(req.getAvatarUrl()));
        d.setSlotMinutesOverride(req.getSlotMinutesOverride());

        syncSpecialties(d, req.getSpecialtyIds());

        d = doctorProfileRepository.save(d);
        return mapResponse(d);
    }

    @Transactional
    public DoctorProfileResponse updateStatus(Long id, UpdateDoctorStatusRequest req) {
        DoctorProfile d = doctorProfileRepository.findById(id)
                                                 .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_PROFILE_NOT_FOUND));

        d.setStatus(req.getStatus());
        d = doctorProfileRepository.save(d);
        accountProvisionService.syncDoctorAccountStatus(d.getId(), req.getStatus());

        return mapResponse(d);
    }

    @Transactional(readOnly = true)
    public DoctorProfileResponse get(Long id) {
        DoctorProfile d = doctorProfileRepository.findById(id)
                                                 .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_PROFILE_NOT_FOUND));
        return mapResponse(d);
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorProfileResponse> list(Long branchId,
                                                    Long specialtyId,
                                                    String q,
                                                    DoctorStatus status,
                                                    Pageable pageable) {
        Page<DoctorProfile> page = doctorProfileRepository.searchAdmin(
                branchId,
                specialtyId,
                (q != null && !q.isBlank()) ? q.trim() : null,
                status,
                pageable
        );

        var items = page.getContent().stream()
                        .map(this::mapResponse)
                        .toList();

        String sort = pageable.getSort().isEmpty() ? null : pageable.getSort().toString();
        return PageResponse.<DoctorProfileResponse>builder()
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
    public StatusSummaryResponse summary(Long branchId, Long specialtyId, String q) {
        long active = 0;
        long inactive = 0;
        String keyword = (q != null && !q.isBlank()) ? q.trim() : null;

        for (var row : doctorProfileRepository.countAdminSummary(branchId, specialtyId, keyword)) {
            if (row.getStatus() == DoctorStatus.ACTIVE) {
                active = row.getCount();
            } else if (row.getStatus() == DoctorStatus.INACTIVE) {
                inactive = row.getCount();
            }
        }

        return StatusSummaryResponse.builder()
                                    .total(active + inactive)
                                    .active(active)
                                    .inactive(inactive)
                                    .build();
    }

    private DoctorProfileResponse mapResponse(DoctorProfile doctor) {
        return DoctorProfileMapper.toResponse(doctor, userRepository.findByDoctorProfile_Id(doctor.getId()).orElse(null));
    }

    private void syncSpecialties(DoctorProfile doctor, List<Long> specialtyIds) {
        Set<Long> requestedIds = specialtyIds == null
                ? Set.of()
                : specialtyIds.stream()
                              .filter(Objects::nonNull)
                              .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Specialty> specialties = requestedIds.isEmpty()
                ? List.of()
                : specialtyRepository.findAllById(requestedIds);

        if (specialties.size() != requestedIds.size()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Có specialtyId không tồn tại");
        }

        Set<Long> existingIds = doctor.getDoctorSpecialties().stream()
                                      .map(ds -> ds.getSpecialty().getId())
                                      .collect(Collectors.toSet());

        doctor.getDoctorSpecialties().removeIf(ds -> !requestedIds.contains(ds.getSpecialty().getId()));

        for (Specialty specialty : specialties) {
            if (existingIds.contains(specialty.getId())) {
                continue;
            }

            DoctorSpecialty doctorSpecialty = DoctorSpecialty.builder()
                                                             .id(new DoctorSpecialtyId(doctor.getId(), specialty.getId()))
                                                             .doctor(doctor)
                                                             .specialty(specialty)
                                                             .build();
            doctor.getDoctorSpecialties().add(doctorSpecialty);
        }
    }
}
