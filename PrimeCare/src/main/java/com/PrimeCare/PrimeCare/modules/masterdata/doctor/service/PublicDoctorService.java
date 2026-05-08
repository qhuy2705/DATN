package com.PrimeCare.PrimeCare.modules.masterdata.doctor.service;

import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAvailabilityService;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.response.DoctorProfileResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
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
        DoctorProfile doctor = doctorProfileRepository.findById(id)
                                                      .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));

        DoctorStatus currentStatus = doctor.getStatus() != null ? doctor.getStatus() : DoctorStatus.ACTIVE;
        if (currentStatus != DoctorStatus.ACTIVE || doctor.getBranch().getStatus() != BranchStatus.ACTIVE) {
            throw new ApiException(ErrorCode.DOCTOR_NOT_FOUND);
        }

        return toResponse(doctor, null);
    }

    private DoctorProfileResponse toResponse(DoctorProfile doctor, Long requestedSpecialtyId) {
        DoctorProfileResponse response = DoctorProfileMapper.toResponse(
                doctor,
                userRepository.findByDoctorProfile_Id(doctor.getId()).orElse(null)
        );

        sanitizeForPublic(response);
        enrichUpcomingAvailability(response, doctor, requestedSpecialtyId);
        return response;
    }


    private void sanitizeForPublic(DoctorProfileResponse response) {
        response.setHasAccount(false);
        response.setAccountId(null);
        response.setAccountEmail(null);
        response.setAccountPhone(null);
        response.setAccountRole(null);
        response.setAccountStatus(null);
    }

    private void enrichUpcomingAvailability(DoctorProfileResponse response, DoctorProfile doctor, Long requestedSpecialtyId) {
        response.setHasUpcomingSchedule(false);
        response.setNextAvailableDate(null);

        if (!response.isBookable()) {
            return;
        }

        Long specialtyId = requestedSpecialtyId;
        if (specialtyId == null && doctor.getDoctorSpecialties() != null) {
            specialtyId = doctor.getDoctorSpecialties().stream()
                                .filter(item -> item.getSpecialty() != null)
                                .map(item -> item.getSpecialty().getId())
                                .findFirst()
                                .orElse(null);
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
