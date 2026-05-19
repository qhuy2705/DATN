package com.PrimeCare.PrimeCare.modules.masterdata.doctor.service;

import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.auth.service.AccountProvisionService;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.CreateDoctorProfileRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.DoctorOptionMode;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.UpdateDoctorProfileRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.UpdateDoctorStatusRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response.DoctorAdminSummaryResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response.DoctorOptionResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response.DoctorProfileResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorSpecialtyId;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.mapper.DoctorProfileMapper;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final AuditLogService auditLogService;

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

        auditLogService.log(null, "CREATE_DOCTOR", "DOCTOR", d.getId(), null, snapshotDoctor(d));

        return mapResponse(d);
    }

    @Transactional
    public DoctorProfileResponse update(Long id, UpdateDoctorProfileRequest req) {
        DoctorProfile d = doctorProfileRepository.findById(id)
                                                 .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_PROFILE_NOT_FOUND));
        Map<String, Object> before = snapshotDoctor(d);

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
        auditLogService.log(null, "UPDATE_DOCTOR", "DOCTOR", d.getId(), before, snapshotDoctor(d));
        return mapResponse(d);
    }

    @Transactional
    public DoctorProfileResponse updateStatus(Long id, UpdateDoctorStatusRequest req) {
        DoctorProfile d = doctorProfileRepository.findById(id)
                                                 .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_PROFILE_NOT_FOUND));
        Map<String, Object> before = snapshotDoctor(d);

        d.setStatus(req.getStatus());
        d = doctorProfileRepository.save(d);
        accountProvisionService.syncDoctorAccountStatus(d.getId(), req.getStatus());

        auditLogService.log(null, "UPDATE_DOCTOR_STATUS", "DOCTOR", d.getId(), before, snapshotDoctor(d));

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
    public DoctorAdminSummaryResponse summary(Long branchId, Long specialtyId, String q, DoctorStatus status) {
        String keyword = (q != null && !q.isBlank()) ? q.trim() : null;
        var row = doctorProfileRepository.summarizeAdmin(branchId, specialtyId, keyword, status);

        long total = row != null ? row.getTotal() : 0;
        long operationalReady = row != null ? row.getOperationalReadyDoctors() : 0;

        return DoctorAdminSummaryResponse.builder()
                                         .total(total)
                                         .active(row != null ? row.getActive() : 0)
                                         .inactive(row != null ? row.getInactive() : 0)
                                         .noAccountDoctors(row != null ? row.getNoAccountDoctors() : 0)
                                         .inactiveAccountDoctors(row != null ? row.getInactiveAccountDoctors() : 0)
                                         .operationalReadyDoctors(operationalReady)
                                         .notOperationalReadyDoctors(Math.max(total - operationalReady, 0))
                                         .build();
    }

    public DoctorAdminSummaryResponse summary(Long branchId, Long specialtyId, String q) {
        return summary(branchId, specialtyId, q, null);
    }

    @Transactional(readOnly = true)
    public List<DoctorOptionResponse> options(
            Long branchId,
            Long specialtyId,
            DoctorStatus status,
            Boolean operationalReady,
            String q,
            DoctorOptionMode mode
    ) {
        DoctorOptionMode effectiveMode = mode != null ? mode : DoctorOptionMode.OPERATIONAL;
        String keyword = (q != null && !q.isBlank()) ? q.trim() : null;
        List<DoctorProfile> doctors = doctorProfileRepository.findOptions(branchId, specialtyId, keyword, status);
        Map<Long, com.PrimeCare.PrimeCare.modules.auth.entity.User> accountByDoctorId = accountsByDoctorId(doctors);

        return doctors.stream()
                .map(doctor -> toOptionResponse(doctor, accountByDoctorId.get(doctor.getId())))
                .filter(option -> includeForMode(option, effectiveMode))
                .filter(option -> operationalReady == null || option.isOperationalReady() == operationalReady)
                .toList();
    }

    private DoctorProfileResponse mapResponse(DoctorProfile doctor) {
        return DoctorProfileMapper.toResponse(doctor, userRepository.findByDoctorProfile_Id(doctor.getId()).orElse(null));
    }

    private Map<Long, com.PrimeCare.PrimeCare.modules.auth.entity.User> accountsByDoctorId(List<DoctorProfile> doctors) {
        List<Long> doctorIds = doctors.stream()
                .map(DoctorProfile::getId)
                .filter(Objects::nonNull)
                .toList();
        if (doctorIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findByDoctorProfile_IdIn(doctorIds)
                .stream()
                .filter(user -> user.getDoctorProfile() != null && user.getDoctorProfile().getId() != null)
                .collect(Collectors.toMap(
                        user -> user.getDoctorProfile().getId(),
                        user -> user,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    private DoctorOptionResponse toOptionResponse(
            DoctorProfile doctor,
            com.PrimeCare.PrimeCare.modules.auth.entity.User account
    ) {
        var readiness = DoctorOperationalGuardService.evaluateReadiness(doctor, account);
        List<DoctorSpecialty> specialties = doctor.getDoctorSpecialties() == null
                ? List.of()
                : doctor.getDoctorSpecialties()
                        .stream()
                        .filter(item -> item.getSpecialty() != null)
                        .sorted(Comparator.comparing(item -> item.getSpecialty().getId()))
                        .toList();
        List<Long> specialtyIds = specialties.stream()
                .map(item -> item.getSpecialty().getId())
                .toList();
        List<String> specialtyNames = specialties.stream()
                .map(item -> {
                    String nameVn = item.getSpecialty().getNameVn();
                    return nameVn != null && !nameVn.isBlank() ? nameVn : item.getSpecialty().getNameEn();
                })
                .filter(Objects::nonNull)
                .toList();

        return DoctorOptionResponse.builder()
                .id(doctor.getId())
                .fullName(doctor.getFullName())
                .branchId(doctor.getBranch() != null ? doctor.getBranch().getId() : null)
                .branchName(resolveBranchName(doctor))
                .specialtyIds(specialtyIds)
                .primarySpecialtyId(specialtyIds.isEmpty() ? null : specialtyIds.get(0))
                .specialtyNames(specialtyNames)
                .profileStatus(doctor.getStatus() != null ? doctor.getStatus() : DoctorStatus.ACTIVE)
                .hasAccount(account != null)
                .accountStatus(account != null ? account.getStatus() : null)
                .operationalReady(readiness.operationalReady())
                .bookable(readiness.operationalReady())
                .notReadyReason(readiness.operationalReady()
                        ? null
                        : DoctorOperationalGuardService.toPublicNotReadyReason(readiness.notReadyReason()))
                .build();
    }

    private boolean includeForMode(DoctorOptionResponse option, DoctorOptionMode mode) {
        return switch (mode) {
            case ADMIN -> true;
            case OPERATIONAL, BOOKABLE -> option.isOperationalReady();
        };
    }

    private String resolveBranchName(DoctorProfile doctor) {
        if (doctor.getBranch() == null) {
            return null;
        }
        String nameVn = doctor.getBranch().getNameVn();
        return nameVn != null && !nameVn.isBlank() ? nameVn : doctor.getBranch().getNameEn();
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

    private Map<String, Object> snapshotDoctor(DoctorProfile doctor) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", doctor.getId());
        data.put("fullName", doctor.getFullName());
        data.put("status", doctor.getStatus() != null ? doctor.getStatus().name() : null);
        data.put("branchId", doctor.getBranch() != null ? doctor.getBranch().getId() : null);
        data.put("specialtyIds", doctor.getDoctorSpecialties() == null
                ? List.of()
                : doctor.getDoctorSpecialties().stream()
                        .map(ds -> ds.getSpecialty() != null ? ds.getSpecialty().getId() : null)
                        .filter(Objects::nonNull)
                        .sorted()
                        .toList());
        data.put("slotMinutesOverride", doctor.getSlotMinutesOverride());
        data.put("yearsExp", doctor.getYearsExp());
        return data;
    }
}
