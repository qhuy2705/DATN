package com.PrimeCare.PrimeCare.modules.masterdata.doctor.service;

import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAvailabilityService;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response.DoctorProfileResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.mapper.DoctorProfileMapper;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.BranchSpecialtyService;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicDoctorService {

    private final DoctorProfileRepository doctorProfileRepository;
    private final UserRepository userRepository;
    private final BranchSpecialtyService branchSpecialtyService;
    private final DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    private final AppointmentAvailabilityService appointmentAvailabilityService;

    @Transactional(readOnly = true)
    public PageResponse<DoctorProfileResponse> search(Long branchId, Long specialtyId, String q, Pageable pageable) {
        if (branchId != null && specialtyId != null) {
            branchSpecialtyService.validateBranchSpecialtyActive(branchId, specialtyId);
        }

        Page<DoctorProfile> page = doctorProfileRepository.search(branchId, specialtyId, q, DoctorStatus.ACTIVE, pageable);

        return PageResponse.<DoctorProfileResponse>builder()
                           .items(page.getContent().stream().map(doctor -> toResponse(doctor, specialtyId)).toList())
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
    public DoctorProfileResponse getById(Long id) {
        DoctorProfile doctor = doctorProfileRepository.findPublicBookableById(id)
                                                      .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));

        return toResponse(doctor, null);
    }

    private DoctorProfileResponse toResponse(DoctorProfile doctor, Long requestedSpecialtyId) {
        DoctorProfileResponse response = DoctorProfileMapper.toResponse(
                doctor,
                userRepository.findByDoctorProfile_Id(doctor.getId()).orElse(null)
        );

        filterPublicSpecialties(response, doctor);
        sanitizeForPublic(response);
        enrichUpcomingAvailability(response, doctor, requestedSpecialtyId);
        return response;
    }


    private void sanitizeForPublic(DoctorProfileResponse response) {
        response.setAccountId(null);
        response.setAccountEmail(null);
        response.setAccountPhone(null);
        response.setAccountRole(null);
        response.setAccountStatus(null);
        response.setOperationalReady(response.isBookable());
        response.setNotReadyReason(response.isBookable()
                ? null
                : DoctorOperationalGuardService.toPublicNotReadyReason(response.getNotReadyReason()));
    }

    private void filterPublicSpecialties(DoctorProfileResponse response, DoctorProfile doctor) {
        List<DoctorSpecialty> publicSpecialties = publicValidSpecialties(doctor);

        response.setSpecialtyIds(publicSpecialties.stream()
                                                  .map(item -> item.getSpecialty().getId())
                                                  .toList());

        if (publicSpecialties.isEmpty()) {
            response.setSpecialtyNameVn(null);
            response.setSpecialtyNameEn(null);
            if (response.isBookable()) {
                response.setBookable(false);
                response.setOperationalReady(false);
                response.setNotReadyReason(DoctorOperationalGuardService.PUBLIC_REASON_NOT_AVAILABLE);
            }
            return;
        }

        var primarySpecialty = publicSpecialties.getFirst().getSpecialty();
        response.setSpecialtyNameVn(primarySpecialty.getNameVn());
        response.setSpecialtyNameEn(primarySpecialty.getNameEn());
    }

    private List<DoctorSpecialty> publicValidSpecialties(DoctorProfile doctor) {
        if (doctor.getDoctorSpecialties() == null || doctor.getDoctorSpecialties().isEmpty()) {
            return List.of();
        }

        return doctor.getDoctorSpecialties().stream()
                     .filter(item -> isPublicValidSpecialty(doctor, item))
                     .sorted(Comparator.comparing(item -> item.getSpecialty().getId()))
                     .toList();
    }

    private boolean isPublicValidSpecialty(DoctorProfile doctor, DoctorSpecialty doctorSpecialty) {
        if (doctor.getBranch() == null
                || doctor.getBranch().getId() == null
                || doctor.getBranch().getStatus() != BranchStatus.ACTIVE
                || doctorSpecialty.getSpecialty() == null
                || doctorSpecialty.getSpecialty().getId() == null
                || !"ACTIVE".equalsIgnoreCase(doctorSpecialty.getSpecialty().getStatus())) {
            return false;
        }

        try {
            branchSpecialtyService.validateBranchSpecialtyActive(
                    doctor.getBranch().getId(),
                    doctorSpecialty.getSpecialty().getId()
            );
            return true;
        } catch (ApiException ignored) {
            return false;
        }
    }

    private void enrichUpcomingAvailability(DoctorProfileResponse response, DoctorProfile doctor, Long requestedSpecialtyId) {
        response.setHasUpcomingSchedule(false);
        response.setNextAvailableDate(null);

        if (!response.isBookable()) {
            return;
        }

        Long specialtyId = requestedSpecialtyId;
        if (specialtyId == null && response.getSpecialtyIds() != null) {
            specialtyId = response.getSpecialtyIds().stream()
                                  .findFirst()
                                  .orElse(null);
        }

        if (specialtyId != null
                && (response.getSpecialtyIds() == null || !response.getSpecialtyIds().contains(specialtyId))) {
            return;
        }

        if (specialtyId == null) {
            return;
        }

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(30);

        var schedules = doctorWorkScheduleRepository.findByDoctor_IdAndWorkDateBetweenOrderByWorkDateAscSessionAsc(
                doctor.getId(),
                from,
                to
        );

        if (schedules.isEmpty()) {
            return;
        }

        for (var schedule : schedules) {
            try {
                var availability = appointmentAvailabilityService.getAvailability(
                        doctor.getBranch().getId(),
                        specialtyId,
                        doctor.getId(),
                        schedule.getWorkDate(),
                        schedule.getSession(),
                        true
                );

                if (availability.getRemainingSlots() > 0 && availability.getSlots() != null && !availability.getSlots().isEmpty()) {
                    response.setHasUpcomingSchedule(true);
                    response.setNextAvailableDate(schedule.getWorkDate().toString());
                    return;
                }
            } catch (ApiException ignored) {
                // skip non-bookable sessions for public preview
            }
        }
    }
}
