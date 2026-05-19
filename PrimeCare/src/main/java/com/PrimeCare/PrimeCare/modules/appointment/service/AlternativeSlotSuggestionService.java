package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.BookableSlotResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AlternativeSlotSuggestionService {

    private static final long MIN_RESPONSE_NOTICE_HOURS = 3L;

    private final AppointmentAvailabilityService availabilityService;
    private final DoctorProfileRepository doctorProfileRepository;

    public Optional<AlternativeSlotSuggestion> suggest(Appointment originalAppointment) {
        if (originalAppointment == null
                || originalAppointment.getDoctor() == null
                || originalAppointment.getBranch() == null
                || originalAppointment.getSpecialty() == null
                || originalAppointment.getVisitDate() == null
                || originalAppointment.getSession() == null) {
            return Optional.empty();
        }

        List<Candidate> candidates = candidates(originalAppointment);
        for (Candidate candidate : candidates) {
            Optional<AlternativeSlotSuggestion> suggestion = firstAvailableSlot(originalAppointment, candidate);
            if (suggestion.isPresent()) {
                return suggestion;
            }
        }
        return Optional.empty();
    }

    private List<Candidate> candidates(Appointment originalAppointment) {
        List<Candidate> candidates = new ArrayList<>();
        Long originalDoctorId = originalAppointment.getDoctor().getId();
        LocalDate date = originalAppointment.getVisitDate();
        BranchSessionType affectedSession = originalAppointment.getSession();

        if (affectedSession == BranchSessionType.AM) {
            candidates.add(new Candidate(originalDoctorId, date, BranchSessionType.PM));
            addOtherDoctorCandidates(candidates, originalAppointment, date, BranchSessionType.AM);
            addOtherDoctorCandidates(candidates, originalAppointment, date, BranchSessionType.PM);
            candidates.add(new Candidate(originalDoctorId, date.plusDays(1), null));
            addOtherDoctorCandidates(candidates, originalAppointment, date.plusDays(1), null);
            addRollingCandidates(candidates, originalAppointment, 3, 7, true, true);
        } else {
            candidates.add(new Candidate(originalDoctorId, date, BranchSessionType.AM));
            addOtherDoctorCandidates(candidates, originalAppointment, date, BranchSessionType.PM);
            candidates.add(new Candidate(originalDoctorId, date.plusDays(1), null));
            addOtherDoctorCandidates(candidates, originalAppointment, date.plusDays(1), null);
            addRollingCandidates(candidates, originalAppointment, 3, 7, true, true);
        }
        return candidates.stream().distinct().toList();
    }

    public Optional<AlternativeSlotSuggestion> suggestForFullDayLeave(Appointment originalAppointment) {
        if (originalAppointment == null || originalAppointment.getVisitDate() == null) {
            return Optional.empty();
        }
        LocalDate date = originalAppointment.getVisitDate();
        List<Candidate> candidates = new ArrayList<>();
        addOtherDoctorCandidates(candidates, originalAppointment, date, null);
        candidates.add(new Candidate(originalAppointment.getDoctor().getId(), date.plusDays(1), null));
        addOtherDoctorCandidates(candidates, originalAppointment, date.plusDays(1), null);
        addRollingCandidates(candidates, originalAppointment, 2, 3, true, false);
        addRollingCandidates(candidates, originalAppointment, 2, 3, false, true);
        addRollingCandidates(candidates, originalAppointment, 4, 7, false, true);
        for (Candidate candidate : candidates.stream().distinct().toList()) {
            Optional<AlternativeSlotSuggestion> suggestion = firstAvailableSlot(originalAppointment, candidate);
            if (suggestion.isPresent()) {
                return suggestion;
            }
        }
        return Optional.empty();
    }

    private void addRollingCandidates(
            List<Candidate> candidates,
            Appointment originalAppointment,
            int fromDayOffset,
            int toDayOffset,
            boolean sameDoctor,
            boolean otherDoctors
    ) {
        LocalDate baseDate = originalAppointment.getVisitDate();
        for (int offset = fromDayOffset; offset <= toDayOffset; offset++) {
            LocalDate date = baseDate.plusDays(offset);
            if (sameDoctor) {
                candidates.add(new Candidate(originalAppointment.getDoctor().getId(), date, null));
            }
            if (otherDoctors) {
                addOtherDoctorCandidates(candidates, originalAppointment, date, null);
            }
        }
    }

    private void addOtherDoctorCandidates(
            List<Candidate> candidates,
            Appointment originalAppointment,
            LocalDate visitDate,
            BranchSessionType session
    ) {
        doctorProfileRepository.findActiveByBranchAndSpecialty(
                        originalAppointment.getBranch().getId(),
                        originalAppointment.getSpecialty().getId()
                )
                .stream()
                .filter(doctor -> !doctor.getId().equals(originalAppointment.getDoctor().getId()))
                .sorted(Comparator.comparing(DoctorProfile::getId))
                .forEach(doctor -> candidates.add(new Candidate(doctor.getId(), visitDate, session)));
    }

    private Optional<AlternativeSlotSuggestion> firstAvailableSlot(Appointment originalAppointment, Candidate candidate) {
        List<BranchSessionType> sessions = candidate.session() != null
                ? List.of(candidate.session())
                : List.of(BranchSessionType.AM, BranchSessionType.PM);
        for (BranchSessionType session : sessions) {
            try {
                AppointmentAvailabilityResponse availability = availabilityService.getAvailabilityExcludingAppointment(
                        originalAppointment.getBranch().getId(),
                        originalAppointment.getSpecialty().getId(),
                        candidate.doctorId(),
                        candidate.visitDate(),
                        session,
                        true,
                        originalAppointment.getId()
                );
                for (BookableSlotResponse slot : availability.getSlots()) {
                    if (slot.isAvailable() && hasEnoughResponseNotice(candidate.visitDate(), slot)) {
                        DoctorProfile doctor = doctorProfileRepository.findById(candidate.doctorId()).orElse(null);
                        if (doctor == null) {
                            continue;
                        }
                        return Optional.of(new AlternativeSlotSuggestion(
                                doctor,
                                candidate.visitDate(),
                                session,
                                slot.getStartTime(),
                                slot.getEndTime()
                        ));
                    }
                }
            } catch (ApiException ignored) {
                // Candidate is not bookable; continue with the next rule.
            }
        }
        return Optional.empty();
    }

    private boolean hasEnoughResponseNotice(LocalDate visitDate, BookableSlotResponse slot) {
        if (slot == null || slot.getStartTime() == null) {
            return false;
        }
        return LocalDateTime.of(visitDate, slot.getStartTime())
                .isAfter(LocalDateTime.now().plusHours(MIN_RESPONSE_NOTICE_HOURS));
    }

    private record Candidate(Long doctorId, LocalDate visitDate, BranchSessionType session) {
    }
}
