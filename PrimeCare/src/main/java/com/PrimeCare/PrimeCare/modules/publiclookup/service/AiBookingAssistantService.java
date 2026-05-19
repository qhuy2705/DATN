package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.BookableSlotResponse;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAvailabilityService;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.entity.DoctorWorkSchedule;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.BranchSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.BranchSpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.medical_service.entity.MedicalService;
import com.PrimeCare.PrimeCare.modules.medical_service.repository.MedicalServiceRepository;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiAvailableSlotResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiBookingDraftResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiConversationContext;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiSelectSlotRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiSuggestedDoctorResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantActionResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantResponse;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.blankToNull;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.containsAny;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.searchable;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiBookingAssistantService {

    private static final int MAX_INITIAL_SLOTS = 3;
    private static final int SEARCH_DAYS = 60;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String PROVIDER = PublicAssistantSafety.PROVIDER_DISPLAY_NAME;

    private final SpecialtyRepository specialtyRepository;
    private final BranchSpecialtyRepository branchSpecialtyRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final BranchRepository branchRepository;
    private final DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    private final AppointmentAvailabilityService appointmentAvailabilityService;
    private final MedicalServiceRepository medicalServiceRepository;

    private final Cache<String, AiConversationContext> contextCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofHours(2))
            .build();

    @Transactional(readOnly = true)
    public Optional<PublicAssistantResponse> tryHandle(PublicAssistantRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        String question = Objects.toString(request.getQuestion(), "").trim();
        String normalized = searchable(question);
        AiConversationContext context = resolveContext(request);

        Optional<PublicAssistantSafety.SafetyBlock> safetyBlock = PublicAssistantSafety.detect(question);
        if (safetyBlock.isPresent()) {
            return Optional.of(safetyResponse(context, safetyBlock.get()));
        }

        String actionType = normalizeActionType(request.getActionType());
        LocalDate datePreference = parseDatePreference(question, normalized).orElse(null);
        TimePreference timePreference = parseTimePreference(question, normalized);

        String payloadSlotId = slotIdFromPayload(request);
        if ("SELECT_SLOT".equals(actionType) || payloadSlotId != null) {
            return Optional.of(selectSlot(context, payloadSlotId, "SELECT_SLOT"));
        }

        SlotSelectionMatch selectionMatch = matchSlotSelection(question, normalized, context);
        if (selectionMatch.attempted()) {
            if (selectionMatch.slot() != null && !selectionMatch.ambiguous()) {
                return Optional.of(selectSlot(context, selectionMatch.slot().getSlotId(), "SELECT_SLOT"));
            }
            return Optional.of(clarification(
                    context,
                    "SELECT_SLOT",
                    "Tôi chưa xác định chắc chắn ca bạn muốn chọn. Bạn chọn trực tiếp một ca trong danh sách giúp tôi nhé.",
                    context.getLastAvailableSlots()
            ));
        }

        if ("VIEW_MORE_SLOTS".equals(actionType) || (hasBookingContext(context) && isViewMoreSlots(normalized))) {
            return Optional.of(viewMoreSlots(context));
        }

        if ("CHANGE_DOCTOR".equals(actionType) || isChangeDoctor(normalized)) {
            return Optional.of(changeDoctor(context));
        }

        if ("VIEW_FACILITY_INFO".equals(actionType)
                || (hasBookingContext(context) && isFacilityInfoFollowUpQuestion(normalized))) {
            return Optional.of(facilityInfo(context, normalized));
        }

        Optional<PublicAssistantResponse> publicContextResponse =
                tryHandlePublicCareContext(context, question, normalized, datePreference, timePreference);
        if (publicContextResponse.isPresent()) {
            return publicContextResponse;
        }

        if (classifyRouteIntent(request, normalized, context) != AssistantRouteIntent.BOOKING) {
            return Optional.empty();
        }

        if ((timePreference != null || datePreference != null) && hasDoctorOrSpecialtyContext(context)) {
            return Optional.of(handleAvailabilityPreference(context, datePreference, timePreference));
        }

        Optional<DoctorProfile> doctorFromContextOrQuestion = resolveDoctor(context, question, normalized);
        if (doctorFromContextOrQuestion.isPresent() && isDoctorQuestion(normalized)) {
            return Optional.of(handleDoctorQuestion(context, doctorFromContextOrQuestion.get(), normalized));
        }

        SpecialtyResolution specialtyResolution = resolveSpecialtyResolutionForQuestion(question, normalized).orElse(null);
        Specialty specialty = specialtyResolution != null ? specialtyResolution.specialty() : null;
        boolean symptom = specialtyResolution != null && specialtyResolution.symptom();
        boolean newSymptomRequest = specialtyResolution != null && specialtyResolution.symptomGroup() != null;
        if (specialty == null && context.getCurrentSpecialtyId() != null && !newSymptomRequest && isAvailabilityQuestion(normalized)) {
            specialty = specialtyRepository.findById(context.getCurrentSpecialtyId()).orElse(null);
        }
        if (specialty != null) {
            String intent = symptom ? "SYMPTOM_TO_SPECIALTY" : "SPECIALTY_SEARCH";
            return Optional.of(recommendDoctorAndSlots(
                    context,
                    specialty,
                    datePreference,
                    timePreference,
                    intent,
                    specialtyResolution != null ? specialtyResolution.symptomGroup() : null
            ));
        }

        if (doctorFromContextOrQuestion.isPresent()) {
            DoctorProfile doctor = doctorFromContextOrQuestion.get();
            Specialty doctorSpecialty = resolveDoctorSpecialty(doctor, context.getCurrentSpecialtyId()).orElse(null);
            if (doctorSpecialty == null) {
                return Optional.of(clarification(
                        context,
                        "CLARIFICATION",
                        "Tôi đã tìm thấy bác sĩ, nhưng chưa xác định được chuyên khoa để kiểm tra lịch. Bạn muốn khám chuyên khoa nào?",
                        List.of()
                ));
            }
            return Optional.of(showSlotsForDoctor(context, doctor, doctorSpecialty, datePreference, timePreference, "SHOW_AVAILABLE_SLOTS", Set.of()));
        }

        return Optional.of(clarification(
                context,
                "CLARIFICATION",
                "Bạn muốn tôi tìm lịch theo chuyên khoa, bác sĩ hay triệu chứng nào?",
                List.of()
        ));
    }

    @Transactional(readOnly = true)
    public PublicAssistantResponse chat(PublicAssistantRequest request) {
        return tryHandle(request).orElseGet(() -> {
            AiConversationContext context = resolveContext(request);
            String normalized = searchable(request == null ? null : request.getQuestion());
            if (isAmbiguousSymptom(normalized)) {
                return clarification(
                        context,
                        "CLARIFICATION",
                        "Bạn cho tôi biết rõ hơn vị trí đau hoặc triệu chứng chính để tôi gợi ý chuyên khoa phù hợp.",
                        List.of()
                );
            }
            return clarification(
                    context,
                    "CLARIFICATION",
                    "Bạn mô tả triệu chứng, chuyên khoa hoặc bác sĩ muốn khám, tôi sẽ tìm lịch trống gần nhất từ dữ liệu hiện có.",
                    List.of()
            );
        });
    }

    @Transactional(readOnly = true)
    public PublicAssistantResponse selectSlot(AiSelectSlotRequest request) {
        PublicAssistantRequest assistantRequest = new PublicAssistantRequest();
        assistantRequest.setQuestion("Chọn ca khám");
        assistantRequest.setConversationId(request != null ? request.getConversationId() : null);
        assistantRequest.setContext(request != null ? request.getContext() : null);
        assistantRequest.setActionType("SELECT_SLOT");
        if (request != null && request.getSlotId() != null) {
            assistantRequest.setActionPayload(Map.of("slotId", request.getSlotId()));
        }
        return selectSlot(resolveContext(assistantRequest), request != null ? request.getSlotId() : null, "SELECT_SLOT");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> searchAvailableSlotsForTool(String specialtyKeyword, String dateKeyword) {
        String normalized = searchable(specialtyKeyword);
        LocalDate fromDate = parseDateKeyword(dateKeyword).orElse(LocalDate.now());
        Specialty specialty = resolveSpecialtyResolutionForQuestion(specialtyKeyword, normalized)
                .map(SpecialtyResolution::specialty)
                .or(() -> resolveSpecialtyForStandaloneKeyword(normalized))
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", Map.of(
                "specialtyKeyword", specialtyKeyword != null ? specialtyKeyword : "",
                "fromDate", fromDate.toString()
        ));

        if (specialty == null) {
            result.put("totalSlots", 0);
            result.put("slots", List.of());
            result.put("message", "Không tìm thấy chuyên khoa phù hợp trong dữ liệu hiện có.");
            return result;
        }

        List<Map<String, Object>> slots = new ArrayList<>();
        for (DoctorProfile doctor : findDoctorsBySpecialty(specialty.getId(), null)) {
            List<AiAvailableSlotResponse> doctorSlots = findNearestSlotsForDoctor(
                    doctor,
                    specialty,
                    fromDate,
                    MAX_INITIAL_SLOTS,
                    null,
                    Set.of()
            );
            for (AiAvailableSlotResponse slot : doctorSlots) {
                slots.add(slotToolMap(slot));
            }
            if (!slots.isEmpty()) {
                break;
            }
        }

        result.put("specialtyId", specialty.getId());
        result.put("specialtyName", specialtyName(specialty));
        result.put("totalSlots", slots.size());
        result.put("slots", slots);
        if (slots.isEmpty()) {
            result.put("message", "Chưa tìm thấy lịch trống phù hợp từ dữ liệu hiện có.");
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<AiBookingDraftResponse> validateBookingDraftPayload(Map<String, Object> payload) {
        String slotId = slotIdFromPayload(payload);
        SlotKey slotKey = parseSlotId(slotId).orElse(null);
        if (slotKey == null) {
            return Optional.empty();
        }

        SlotValidation validation = validateSlot(slotKey);
        if (!validation.available() || validation.slot() == null) {
            return Optional.empty();
        }
        return Optional.of(bookingDraft(validation.slot()));
    }

    private AssistantRouteIntent classifyRouteIntent(PublicAssistantRequest request, String normalized, AiConversationContext context) {
        if (blankToNull(request.getActionType()) != null || slotIdFromPayload(request) != null) {
            return AssistantRouteIntent.BOOKING;
        }
        if (isSystemFaqIntent(normalized, context)) {
            return AssistantRouteIntent.SYSTEM_FAQ;
        }
        if (hasBookingContext(context) && isBookingFollowUp(normalized)) {
            return AssistantRouteIntent.BOOKING;
        }
        if (hasExplicitBookingIntent(normalized)) {
            return AssistantRouteIntent.BOOKING;
        }
        if (hasControlledSymptomIntent(normalized)) {
            return AssistantRouteIntent.BOOKING;
        }
        if (hasDirectSpecialtySignal(normalized)) {
            return AssistantRouteIntent.BOOKING;
        }
        return AssistantRouteIntent.OUT_OF_SCOPE;
    }

    private boolean isSystemFaqIntent(String normalized, AiConversationContext context) {
        if (containsAnyPhrase(normalized, List.of(
                "quen mat khau",
                "mat khau",
                "dang nhap",
                "dang ky",
                "otp",
                "ho so benh an",
                "ho so",
                "don thuoc cu",
                "xem don thuoc",
                "nha thuoc",
                "ke don sau khi kham",
                "bac si co ke don",
                "ket qua kham",
                "ket qua",
                "tra cuu ket qua",
                "lich hen cua toi",
                "lich hen",
                "huy lich",
                "huy lich hen",
                "doi lich",
                "doi lich hen",
                "thanh toan",
                "bao hiem",
                "cach dat lich",
                "quy trinh kham",
                "gio lam viec",
                "chinh sach"
        ))) {
            return true;
        }
        return !hasBookingContext(context) && isFacilityQuestion(normalized);
    }

    private boolean isBookingFollowUp(String normalized) {
        return containsAnyPhrase(normalized, List.of(
                "co so nay",
                "co so",
                "dia chi",
                "o dau",
                "gan hon",
                "con gio",
                "gio khac",
                "ca khac",
                "xem them",
                "bac si khac",
                "doi bac si",
                "chon",
                "lay lich",
                "ca dau",
                "ca thu",
                "slot thu",
                "buoi sang",
                "buoi chieu",
                "ngay mai",
                "hom nay",
                "tuan sau",
                "thu 2",
                "thu hai",
                "thu 3",
                "thu ba",
                "thu 4",
                "thu tu",
                "thu 5",
                "thu nam",
                "thu 6",
                "thu sau",
                "thu 7",
                "thu bay",
                "chu nhat",
                "lich trong",
                "con lich",
                "som nhat",
                "gan nhat",
                "lich gan nhat",
                "tim lich gan nhat"
        )) || parseTimePreference(null, normalized) != null || parseDatePreference(null, normalized).isPresent();
    }

    private boolean hasExplicitBookingIntent(String normalized) {
        if (containsAnyPhrase(normalized, List.of(
                "dat lich",
                "muon dat lich",
                "lich kham",
                "lich trong",
                "slot",
                "ca kham",
                "muon kham",
                "can kham",
                "chuyen khoa",
                "khoa nao",
                "bac si",
                "doctor",
                "specialty",
                "appointment",
                "available"
        ))) {
            return true;
        }
        if (containsAnyPhrase(normalized, List.of("som nhat", "gan nhat", "tim lich", "lich gan nhat"))) {
            return true;
        }
        return containsPhrase(normalized, "kham") && (hasControlledSymptomIntent(normalized) || hasDirectSpecialtySignal(normalized));
    }

    private boolean hasPublicDoctorBookingIntent(String normalized) {
        return containsAnyPhrase(normalized, List.of(
                "dat lich",
                "muon dat lich",
                "dang ky kham",
                "book",
                "appointment",
                "toi muon kham",
                "muon kham voi",
                "kham voi bac si",
                "kham bac si",
                "som nhat",
                "gan nhat",
                "tim lich gan nhat"
        ));
    }

    private boolean hasControlledSymptomIntent(String normalized) {
        return (hasPediatricContext(normalized) && hasSymptomSignal(normalized))
                || detectSymptomGroup(normalized).isPresent();
    }

    private boolean isAmbiguousSymptom(String normalized) {
        return containsAnyPhrase(normalized, List.of(
                "toi dau",
                "toi hoi dau mot chut",
                "hoi dau mot chut",
                "toi kho chiu",
                "toi thay la lam"
        ));
    }

    private boolean hasDirectSpecialtySignal(String normalized) {
        return detectDirectSpecialtyGroup(normalized).isPresent();
    }

    private boolean hasBookingContext(AiConversationContext context) {
        return context != null && (context.getCurrentDoctorId() != null
                || context.getCurrentSpecialtyId() != null
                || context.getCurrentFacilityId() != null
                || (context.getLastAvailableSlots() != null && !context.getLastAvailableSlots().isEmpty()));
    }

    private PublicAssistantResponse recommendDoctorAndSlots(AiConversationContext context,
                                                            Specialty specialty,
                                                            LocalDate fromDate,
                                                            TimePreference timePreference,
                                                            String intent,
                                                            AssistantSpecialtyGroup specialtyGroup) {
        resetContextForNewSpecialty(context, specialty);
        applySchedulePreference(context, fromDate, timePreference);
        List<DoctorProfile> doctors = findDoctorsBySpecialty(specialty.getId(), null);
        if (doctors.isEmpty()) {
            context.setCurrentSpecialtyId(specialty.getId());
            context.setCurrentSpecialtyName(specialtyName(specialty));
            return buildResponse(
                    context,
                    intent,
                    "Hiện tôi chưa tìm thấy bác sĩ đang hoạt động cho " + specialtyName(specialty) + ". Bạn có thể chọn chuyên khoa khác hoặc quay lại sau.",
                    null,
                    List.of(),
                    null,
                    List.of(changeDoctorAction(), viewMoreSlotsAction()),
                    true
            );
        }

        DoctorSlots best = null;
        DoctorProfile bestDoctorWithoutSlots = null;
        int bestDoctorWithoutSlotsScore = Integer.MIN_VALUE;
        for (DoctorProfile doctor : doctors) {
            List<AiAvailableSlotResponse> slots = findNearestSlotsForDoctor(
                    doctor,
                    specialty,
                    fromDate == null ? LocalDate.now() : fromDate,
                    MAX_INITIAL_SLOTS,
                    timePreference,
                    Set.of()
            );
            int score = scoreDoctorRecommendation(context, doctor, slots, specialtyGroup);
            boolean expertiseMatched = hasDoctorExpertiseMatch(doctor, specialtyGroup);
            if (slots.isEmpty()) {
                if (score > bestDoctorWithoutSlotsScore) {
                    bestDoctorWithoutSlotsScore = score;
                    bestDoctorWithoutSlots = doctor;
                }
                continue;
            }
            DoctorSlots candidate = new DoctorSlots(doctor, slots, score, expertiseMatched);
            if (isBetterDoctorCandidate(candidate, best)) {
                best = candidate;
            }
        }

        if (best == null) {
            AiSuggestedDoctorResponse suggestedDoctor = bestDoctorWithoutSlots == null
                    ? null
                    : suggestedDoctor(bestDoctorWithoutSlots, specialty);
            if (suggestedDoctor != null) {
                updateContextForDoctor(context, suggestedDoctor, List.of(), intent);
            }
            context.setCurrentSpecialtyId(specialty.getId());
            context.setCurrentSpecialtyName(specialtyName(specialty));
            return buildResponse(
                    context,
                    intent,
                    suggestedDoctor == null
                            ? "Tôi đã tìm thấy " + specialtyName(specialty) + ", nhưng hiện chưa có lịch trống phù hợp. Bạn muốn tôi tìm bác sĩ khác hoặc thời gian khác không?"
                            : "Tôi tìm thấy " + suggestedDoctor.getDoctorName() + " thuộc chuyên khoa " + specialtyName(specialty) + ", nhưng hiện chưa có lịch trống phù hợp. Bạn muốn tôi tìm bác sĩ khác hoặc thời gian khác không?",
                    suggestedDoctor,
                    List.of(),
                    null,
                    List.of(changeDoctorAction(), viewMoreSlotsAction()),
                    false
            );
        }

        return responseForDoctorSlots(
                context,
                best.doctor(),
                specialty,
                best.slots(),
                intent,
                introForSpecialty(specialty, best.doctor(), best.slots(), best.expertiseMatched(), specialtyGroup)
        );
    }

    private void resetContextForNewSpecialty(AiConversationContext context, Specialty specialty) {
        if (context == null || specialty == null || specialty.getId() == null) {
            return;
        }
        if (context.getCurrentSpecialtyId() == null || context.getCurrentSpecialtyId().equals(specialty.getId())) {
            return;
        }
        context.setCurrentDoctorId(null);
        context.setCurrentDoctorName(null);
        context.setCurrentFacilityId(null);
        context.setCurrentFacilityName(null);
        context.setCurrentFacilityAddress(null);
        context.setLastAvailableSlots(List.of());
        context.setLastShownDate(null);
        context.setPendingBookingDraft(null);
        context.setUserTimePreference(null);
        context.setUserSessionType(null);
        context.setPreferredDate(null);
        context.setPreferredFromDate(null);
        context.setPreferredWeekday(null);
        context.setPreferredAfterTime(null);
        context.setPreferredBeforeTime(null);
    }

    private boolean isBetterDoctorCandidate(DoctorSlots candidate, DoctorSlots currentBest) {
        if (candidate == null) {
            return false;
        }
        if (currentBest == null) {
            return true;
        }
        int scoreCompare = Integer.compare(candidate.score(), currentBest.score());
        if (scoreCompare != 0) {
            return scoreCompare > 0;
        }
        return compareSlots(candidate.firstSlot(), currentBest.firstSlot()) < 0;
    }

    private int scoreDoctorRecommendation(AiConversationContext context,
                                          DoctorProfile doctor,
                                          List<AiAvailableSlotResponse> slots,
                                          AssistantSpecialtyGroup specialtyGroup) {
        int score = 1_000;
        int expertiseScore = doctorExpertiseScore(doctor, specialtyGroup);
        score += expertiseScore;
        if (context != null && context.getCurrentFacilityId() != null
                && doctor.getBranch() != null
                && context.getCurrentFacilityId().equals(doctor.getBranch().getId())) {
            score += 80;
        }
        if (doctor.getYearsExp() != null && doctor.getYearsExp() > 0) {
            score += Math.min(doctor.getYearsExp(), 30) * 2;
        }
        AiAvailableSlotResponse firstSlot = slots == null || slots.isEmpty() ? null : slots.get(0);
        if (firstSlot != null && firstSlot.getAppointmentDate() != null) {
            long daysFromToday = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), firstSlot.getAppointmentDate()));
            score -= Math.min(240, (int) daysFromToday * 35);
            LocalTime start = parseSlotTime(firstSlot.getStartTime());
            if (start != null) {
                score -= Math.min(80, start.getHour() * 2);
            }
        }
        return score;
    }

    private boolean hasDoctorExpertiseMatch(DoctorProfile doctor, AssistantSpecialtyGroup specialtyGroup) {
        return doctorExpertiseScore(doctor, specialtyGroup) > 0;
    }

    private int doctorExpertiseScore(DoctorProfile doctor, AssistantSpecialtyGroup specialtyGroup) {
        if (doctor == null || specialtyGroup == null) {
            return 0;
        }
        String corpus = searchable(String.join(" ",
                Objects.toString(doctor.getDisplayTitleVn(), ""),
                Objects.toString(doctor.getDisplayTitleEn(), ""),
                Objects.toString(doctor.getBioVn(), ""),
                Objects.toString(doctor.getBioEn(), ""),
                Objects.toString(doctor.getExpertiseVn(), ""),
                Objects.toString(doctor.getExpertiseEn(), "")
        ));
        if (corpus.isBlank()) {
            return 0;
        }
        int matched = 0;
        List<String> signals = new ArrayList<>();
        signals.addAll(specialtyGroup.symptomKeywords());
        signals.addAll(specialtyGroup.directAliases());
        for (String signal : signals) {
            String normalizedSignal = searchable(signal);
            if (!normalizedSignal.isBlank() && containsPhrase(corpus, normalizedSignal)) {
                matched += Math.min(60, Math.max(20, normalizedSignal.length() * 2));
            }
        }
        if (matched <= 0) {
            return 0;
        }
        return 300 + Math.min(180, matched);
    }

    private PublicAssistantResponse showSlotsForDoctor(AiConversationContext context,
                                                       DoctorProfile doctor,
                                                       Specialty specialty,
                                                       LocalDate fromDate,
                                                       TimePreference timePreference,
                                                       String intent,
                                                       Set<String> skipSlotIds) {
        applySchedulePreference(context, fromDate, timePreference);
        List<AiAvailableSlotResponse> slots = findNearestSlotsForDoctor(
                doctor,
                specialty,
                fromDate == null ? LocalDate.now() : fromDate,
                MAX_INITIAL_SLOTS,
                timePreference,
                skipSlotIds
        );

        if (slots.isEmpty()) {
            AiSuggestedDoctorResponse suggestedDoctor = suggestedDoctor(doctor, specialty);
            updateContextForDoctor(context, suggestedDoctor, List.of(), intent);
            return buildResponse(
                    context,
                    intent,
                    "Hiện chưa có lịch trống phù hợp cho " + doctor.getFullName() + ". Bạn muốn đổi bác sĩ cùng chuyên khoa không?",
                    suggestedDoctor,
                    List.of(),
                    null,
                    List.of(changeDoctorAction()),
                    false
            );
        }

        return responseForDoctorSlots(context, doctor, specialty, slots, intent, introForDoctor(doctor, slots));
    }

    private PublicAssistantResponse responseForDoctorSlots(AiConversationContext context,
                                                           DoctorProfile doctor,
                                                           Specialty specialty,
                                                           List<AiAvailableSlotResponse> slots,
                                                           String intent,
                                                           String message) {
        AiSuggestedDoctorResponse suggestedDoctor = suggestedDoctor(doctor, specialty);
        updateContextForDoctor(context, suggestedDoctor, slots, intent);
        return buildResponse(
                context,
                intent,
                message,
                suggestedDoctor,
                slots,
                null,
                slotActions(slots, true),
                false
        );
    }

    private PublicAssistantResponse viewMoreSlots(AiConversationContext context) {
        Optional<DoctorProfile> doctor = loadDoctor(context.getCurrentDoctorId());
        Optional<Specialty> specialty = loadSpecialty(context.getCurrentSpecialtyId());
        if (doctor.isEmpty() || specialty.isEmpty()) {
            return clarification(
                    context,
                    "VIEW_MORE_SLOTS",
                    "Bạn muốn xem thêm giờ của bác sĩ hoặc chuyên khoa nào?",
                    List.of()
            );
        }

        Set<String> skipped = context.getLastAvailableSlots() == null
                ? Set.of()
                : context.getLastAvailableSlots().stream()
                        .map(AiAvailableSlotResponse::getSlotId)
                        .filter(Objects::nonNull)
                        .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        LocalDate fromDate = preferredSearchDate(context);
        TimePreference timePreference = parseStoredTimePreference(context.getUserTimePreference());
        applySchedulePreference(context, fromDate, timePreference);
        List<AiAvailableSlotResponse> slots = findNearestSlotsForDoctor(
                doctor.get(),
                specialty.get(),
                fromDate,
                MAX_INITIAL_SLOTS,
                timePreference,
                skipped
        );

        if (slots.isEmpty()) {
            AiSuggestedDoctorResponse suggestedDoctor = suggestedDoctor(doctor.get(), specialty.get());
            updateContextForDoctor(context, suggestedDoctor, List.of(), "VIEW_MORE_SLOTS");
            return buildResponse(
                    context,
                    "VIEW_MORE_SLOTS",
                    noMoreSlotsMessage(context),
                    suggestedDoctor,
                    List.of(),
                    null,
                    List.of(changeDoctorAction()),
                    false
            );
        }

        return responseForDoctorSlots(
                context,
                doctor.get(),
                specialty.get(),
                slots,
                "VIEW_MORE_SLOTS",
                viewMoreSlotsMessage(context, slots, doctor.get())
        );
    }

    private LocalDate preferredSearchDate(AiConversationContext context) {
        if (context == null) {
            return LocalDate.now();
        }
        if (context.getPreferredFromDate() != null) {
            return context.getPreferredFromDate();
        }
        if (context.getPreferredDate() != null) {
            return context.getPreferredDate();
        }
        if (context.getLastShownDate() != null) {
            return context.getLastShownDate();
        }
        return LocalDate.now();
    }

    private String viewMoreSlotsMessage(AiConversationContext context, List<AiAvailableSlotResponse> slots, DoctorProfile doctor) {
        LocalDate requestedDate = preferredSearchDate(context);
        LocalDate returnedDate = slots == null || slots.isEmpty() ? null : slots.get(0).getAppointmentDate();
        if (requestedDate != null && returnedDate != null && returnedDate.isAfter(requestedDate)) {
            return "Ngày " + formatDate(requestedDate) + " đã hết ca phù hợp theo lựa chọn hiện tại. PrimeCare AI tìm thấy ca gần nhất tiếp theo cho " + doctor.getFullName() + ".";
        }
        return "PrimeCare AI đã tìm thấy thêm một số lịch trống phù hợp cho " + doctor.getFullName() + ".";
    }

    private String noMoreSlotsMessage(AiConversationContext context) {
        LocalDate requestedDate = preferredSearchDate(context);
        if (requestedDate != null && requestedDate.isAfter(LocalDate.now().minusDays(1))) {
            return "Ngày " + formatDate(requestedDate) + " đã hết ca phù hợp theo lựa chọn hiện tại. Bạn muốn đổi bác sĩ hoặc chọn thời gian khác không?";
        }
        return "Tôi chưa tìm thấy thêm ca trống phù hợp. Bạn muốn đổi bác sĩ cùng chuyên khoa không?";
    }

    private PublicAssistantResponse changeDoctor(AiConversationContext context) {
        if (context.getCurrentSpecialtyId() == null) {
            return clarification(context, "CHANGE_DOCTOR", "Bạn muốn đổi bác sĩ trong chuyên khoa nào?", List.of());
        }

        Specialty specialty = specialtyRepository.findById(context.getCurrentSpecialtyId()).orElse(null);
        if (specialty == null) {
            return clarification(context, "CHANGE_DOCTOR", "Tôi chưa xác định được chuyên khoa để đổi bác sĩ.", List.of());
        }

        Set<Long> excludedDoctorIds = new HashSet<>();
        if (context.getCurrentDoctorId() != null) {
            excludedDoctorIds.add(context.getCurrentDoctorId());
        }
        if (context.getLastSuggestedDoctors() != null) {
            context.getLastSuggestedDoctors().stream()
                    .map(AiSuggestedDoctorResponse::getDoctorId)
                    .filter(Objects::nonNull)
                    .forEach(excludedDoctorIds::add);
        }

        List<DoctorProfile> alternatives = findDoctorsBySpecialty(specialty.getId(), excludedDoctorIds);
        if (alternatives.isEmpty() && context.getCurrentDoctorId() != null) {
            alternatives = findDoctorsBySpecialty(specialty.getId(), Set.of(context.getCurrentDoctorId()));
        }

        for (DoctorProfile doctor : alternatives) {
            List<AiAvailableSlotResponse> slots = findNearestSlotsForDoctor(
                    doctor,
                    specialty,
                    preferredSearchDate(context),
                    MAX_INITIAL_SLOTS,
                    parseStoredTimePreference(context.getUserTimePreference()),
                    Set.of()
            );
            if (!slots.isEmpty()) {
                return responseForDoctorSlots(
                        context,
                        doctor,
                        specialty,
                        slots,
                        "CHANGE_DOCTOR",
                        "Tôi gợi ý bác sĩ khác cùng " + specialtyName(specialty) + ": " + doctor.getFullName()
                                + " tại " + facilityName(doctor.getBranch()) + ". PrimeCare AI đã tìm thấy một số lịch trống phù hợp."
                );
            }
        }

        return buildResponse(
                context,
                "CHANGE_DOCTOR",
                "Hiện tôi chưa tìm thấy bác sĩ khác cùng chuyên khoa còn lịch trống.",
                null,
                List.of(),
                null,
                List.of(viewMoreSlotsAction()),
                false
        );
    }

    private PublicAssistantResponse facilityInfo(AiConversationContext context, String normalized) {
        if (containsAny(normalized, "co so khac", "phong kham khac", "branch khac", "facility khac")) {
            return clarification(
                    context,
                    "FACILITY_INFO",
                    "Bạn cho tôi biết khu vực hoặc quận/huyện muốn khám, tôi sẽ kiểm tra cơ sở phù hợp từ dữ liệu PrimeCare. Tôi sẽ không tự đoán vị trí của bạn.",
                    context.getLastAvailableSlots()
            );
        }
        if (containsAny(normalized, "gan hon", "gan toi", "nearest", "closer")) {
            return clarification(
                    context,
                    "FACILITY_INFO",
                    "Bạn cho tôi biết khu vực hoặc quận/huyện muốn khám, tôi mới có thể lọc cơ sở phù hợp hơn. Tôi sẽ không tự đoán vị trí của bạn.",
                    context.getLastAvailableSlots()
            );
        }

        Long facilityId = context.getCurrentFacilityId();
        if (facilityId == null && context.getCurrentDoctorId() != null) {
            facilityId = loadDoctor(context.getCurrentDoctorId())
                    .map(DoctorProfile::getBranch)
                    .map(Branch::getId)
                    .orElse(null);
        }

        if (facilityId == null) {
            return clarification(
                    context,
                    "FACILITY_INFO",
                    "Bạn muốn xem địa chỉ của cơ sở nào?",
                    context.getLastAvailableSlots()
            );
        }

        Branch branch = branchRepository.findById(facilityId).orElse(null);
        if (branch == null || branch.getStatus() != BranchStatus.ACTIVE) {
            return clarification(context, "FACILITY_INFO", "Tôi chưa tìm thấy thông tin cơ sở này trong dữ liệu hiện có.", List.of());
        }

        context.setCurrentFacilityId(branch.getId());
        context.setCurrentFacilityName(facilityName(branch));
        context.setCurrentFacilityAddress(facilityAddress(branch));
        context.setLastIntent("FACILITY_INFO");
        context.setLastAiAction("FACILITY_INFO");
        context.setClarificationNeeded(false);

        String message = facilityName(branch) + " ở " + facilityAddress(branch) + ".";
        if (blankToNull(branch.getPhone()) != null) {
            message += " Số điện thoại cơ sở: " + branch.getPhone().trim() + ".";
        }
        return buildResponse(
                context,
                "FACILITY_INFO",
                message,
                null,
                List.of(),
                null,
                List.of(),
                false
        );
    }

    private PublicAssistantResponse handleAvailabilityPreference(AiConversationContext context,
                                                                 LocalDate fromDate,
                                                                 TimePreference timePreference) {
        TimePreference effectiveTimePreference = timePreference != null
                ? timePreference
                : parseStoredTimePreference(context.getUserTimePreference());
        applySchedulePreference(context, fromDate, effectiveTimePreference);
        Optional<DoctorProfile> doctor = loadDoctor(context.getCurrentDoctorId());
        Optional<Specialty> specialty = loadSpecialty(context.getCurrentSpecialtyId());
        if (doctor.isPresent() && specialty.isPresent()) {
            return showSlotsForDoctor(context, doctor.get(), specialty.get(), fromDate, effectiveTimePreference, "TIME_PREFERENCE", Set.of());
        }
        if (specialty.isPresent()) {
            return recommendDoctorAndSlots(context, specialty.get(), fromDate, effectiveTimePreference, "TIME_PREFERENCE", null);
        }
        return clarification(context, "TIME_PREFERENCE", "Bạn muốn tìm giờ này cho chuyên khoa hoặc bác sĩ nào?", List.of());
    }

    private PublicAssistantResponse handleDoctorQuestion(AiConversationContext context, DoctorProfile doctor, String normalized) {
        Specialty specialty = resolveDoctorSpecialty(doctor, context.getCurrentSpecialtyId()).orElse(null);

        if (containsAny(normalized, "chuyen khoa gi", "chuyen gi", "khoa gi", "specialty")) {
            List<String> specialtyNames = publicValidSpecialties(doctor).stream()
                    .map(this::specialtyName)
                    .distinct()
                    .toList();
            String message = specialtyNames.isEmpty()
                    ? "Tôi chưa có dữ liệu chuyên khoa công khai của " + doctor.getFullName() + "."
                    : doctor.getFullName() + " đang khám " + String.join(", ", specialtyNames) + ".";
            AiSuggestedDoctorResponse suggestedDoctor = specialty != null ? suggestedDoctor(doctor, specialty) : null;
            if (suggestedDoctor != null) {
                updateContextForDoctor(context, suggestedDoctor, context.getLastAvailableSlots(), "DOCTOR_RECOMMENDATION");
            }
            return buildResponse(context, "DOCTOR_RECOMMENDATION", message, suggestedDoctor, List.of(), null, List.of(), false);
        }

        if (containsAny(normalized, "o dau", "co so", "dia chi", "phong kham")) {
            if (doctor.getBranch() != null) {
                context.setCurrentDoctorId(doctor.getId());
                context.setCurrentDoctorName(doctor.getFullName());
                context.setCurrentFacilityId(doctor.getBranch().getId());
                context.setCurrentFacilityName(facilityName(doctor.getBranch()));
                context.setCurrentFacilityAddress(facilityAddress(doctor.getBranch()));
            }
            return facilityInfo(context, normalized);
        }

        if (specialty == null) {
            return clarification(context, "DOCTOR_RECOMMENDATION", "Bạn muốn xem lịch của bác sĩ này theo chuyên khoa nào?", List.of());
        }

        return showSlotsForDoctor(context, doctor, specialty, null, null, "SHOW_AVAILABLE_SLOTS", Set.of());
    }

    private PublicAssistantResponse selectSlot(AiConversationContext context, String slotId, String intent) {
        if (blankToNull(slotId) == null) {
            return clarification(context, intent, "Bạn chọn giúp tôi một ca khám cụ thể trong danh sách.", context.getLastAvailableSlots());
        }

        SlotKey slotKey = parseSlotId(slotId).orElse(null);
        AiAvailableSlotResponse slotFromContext = findSlotInContext(context, slotId).orElse(null);
        if (slotKey == null && slotFromContext != null) {
            slotKey = keyFromSlot(slotFromContext);
        }
        if (slotKey == null) {
            return clarification(context, intent, "Tôi chưa đọc được ca khám này. Bạn chọn lại trong danh sách giúp tôi nhé.", context.getLastAvailableSlots());
        }

        if (context.getCurrentDoctorId() != null && !context.getCurrentDoctorId().equals(slotKey.doctorId())) {
            return clarification(context, intent, "Ca này không thuộc bác sĩ đang được gợi ý trong hội thoại. Bạn chọn lại giúp tôi nhé.", context.getLastAvailableSlots());
        }
        if (context.getCurrentFacilityId() != null && !context.getCurrentFacilityId().equals(slotKey.facilityId())) {
            return clarification(context, intent, "Ca này không thuộc cơ sở đang được gợi ý trong hội thoại. Bạn chọn lại giúp tôi nhé.", context.getLastAvailableSlots());
        }

        SlotValidation validation = validateSlot(slotKey);
        if (!validation.available()) {
            List<AiAvailableSlotResponse> alternatives = validation.alternativeSlots();
            AiSuggestedDoctorResponse suggestedDoctor = validation.suggestedDoctor();
            updateContextForDoctor(context, suggestedDoctor, alternatives, intent);
            return buildResponse(
                    context,
                    intent,
                    "Ca khám này hiện không còn khả dụng. PrimeCare AI sẽ tìm ca gần nhất khác cho bạn.",
                    suggestedDoctor,
                    alternatives,
                    null,
                    slotActions(alternatives, true),
                    false
            );
        }

        AiAvailableSlotResponse selected = validation.slot();
        AiBookingDraftResponse bookingDraft = bookingDraft(selected);
        context.setPendingBookingDraft(bookingDraft);
        context.setLastIntent("BOOKING_DRAFT_CREATED");
        context.setLastAiAction("BOOKING_DRAFT_CREATED");
        context.setClarificationNeeded(false);
        context.setCurrentDoctorId(selected.getDoctorId());
        context.setCurrentDoctorName(selected.getDoctorName());
        context.setCurrentSpecialtyId(selected.getSpecialtyId());
        context.setCurrentSpecialtyName(selected.getSpecialtyName());
        context.setCurrentFacilityId(selected.getFacilityId());
        context.setCurrentFacilityName(selected.getFacilityName());
        context.setCurrentFacilityAddress(selected.getFacilityAddress());

        String message = "Tôi đã tạo bản nháp đặt lịch và sẽ chuyển bạn sang màn đặt lịch để xác nhận.";

        return buildResponse(
                context,
                "BOOKING_DRAFT_CREATED",
                message,
                null,
                List.of(),
                bookingDraft,
                List.of(goToBookingAction(bookingDraft)),
                false
        );
    }

    private SlotValidation validateSlot(SlotKey key) {
        if (key == null || key.appointmentDate() == null || key.appointmentDate().isBefore(LocalDate.now())) {
            return new SlotValidation(false, null, null, List.of());
        }
        DoctorProfile doctor = doctorProfileRepository.findPublicBookableById(key.doctorId()).orElse(null);
        Specialty specialty = specialtyRepository.findById(key.specialtyId()).orElse(null);
        Branch branch = doctor == null ? null : doctor.getBranch();
        if (doctor == null
                || specialty == null
                || branch == null
                || doctor.getStatus() != DoctorStatus.ACTIVE
                || !"ACTIVE".equalsIgnoreCase(Objects.toString(specialty.getStatus(), ""))
                || !key.facilityId().equals(branch.getId())
                || !doctorHasSpecialty(doctor, key.specialtyId())) {
            return new SlotValidation(false, null, null, List.of());
        }

        try {
            AppointmentAvailabilityResponse availability = appointmentAvailabilityService.getAvailability(
                    key.facilityId(),
                    key.specialtyId(),
                    key.doctorId(),
                    key.appointmentDate(),
                    key.session(),
                    false
            );
            BookableSlotResponse matched = availability.getSlots() == null
                    ? null
                    : availability.getSlots().stream()
                            .filter(slot -> key.startTime().equals(slot.getStartTime()))
                            .findFirst()
                            .orElse(null);

            boolean available = matched != null
                    && matched.isAvailable()
                    && appointmentAvailabilityService.isSlotStillBookable(key.appointmentDate(), key.startTime());
            if (!available) {
                return new SlotValidation(
                        false,
                        null,
                        suggestedDoctor(doctor, specialty),
                        findNearestSlotsForDoctor(doctor, specialty, LocalDate.now(), MAX_INITIAL_SLOTS, null, Set.of(key.slotId()))
                );
            }
            return new SlotValidation(true, toAiSlot(doctor, specialty, key.appointmentDate(), key.session(), matched), suggestedDoctor(doctor, specialty), List.of());
        } catch (ApiException ex) {
            return new SlotValidation(
                    false,
                    null,
                    suggestedDoctor(doctor, specialty),
                    findNearestSlotsForDoctor(doctor, specialty, LocalDate.now(), MAX_INITIAL_SLOTS, null, Set.of(key.slotId()))
            );
        }
    }

    private boolean doctorHasSpecialty(DoctorProfile doctor, Long specialtyId) {
        if (doctor == null || specialtyId == null || doctor.getDoctorSpecialties() == null) {
            return false;
        }
        return publicValidSpecialties(doctor).stream()
                .anyMatch(specialty -> specialtyId.equals(specialty.getId()));
    }

    private List<AiAvailableSlotResponse> findNearestSlotsForDoctor(DoctorProfile doctor,
                                                                    Specialty specialty,
                                                                    LocalDate fromDate,
                                                                    int limit,
                                                                    TimePreference timePreference,
                                                                    Set<String> skipSlotIds) {
        if (doctor == null || doctor.getId() == null || doctor.getBranch() == null || specialty == null || specialty.getId() == null) {
            return List.of();
        }

        LocalDate safeFrom = fromDate == null || fromDate.isBefore(LocalDate.now()) ? LocalDate.now() : fromDate;
        LocalDate toDate = safeFrom.plusDays(SEARCH_DAYS);
        List<DoctorWorkSchedule> schedules = doctorWorkScheduleRepository
                .findByDoctor_IdAndWorkDateBetweenOrderByWorkDateAscSessionAsc(doctor.getId(), safeFrom, toDate);
        if (schedules == null || schedules.isEmpty()) {
            return List.of();
        }

        Set<String> skipped = skipSlotIds == null ? Set.of() : skipSlotIds;
        Map<LocalDate, List<AiAvailableSlotResponse>> slotsByDate = new LinkedHashMap<>();
        for (DoctorWorkSchedule schedule : schedules) {
            if (schedule == null || schedule.getWorkDate() == null || schedule.getSession() == null) {
                continue;
            }
            try {
                AppointmentAvailabilityResponse availability = appointmentAvailabilityService.getAvailability(
                        doctor.getBranch().getId(),
                        specialty.getId(),
                        doctor.getId(),
                        schedule.getWorkDate(),
                        schedule.getSession(),
                        true
                );
                if (availability.getSlots() == null || availability.getSlots().isEmpty()) {
                    continue;
                }
                for (BookableSlotResponse slot : availability.getSlots()) {
                    if (slot == null || !slot.isAvailable() || !matchesTimePreference(slot.getStartTime(), timePreference)) {
                        continue;
                    }
                    AiAvailableSlotResponse aiSlot = toAiSlot(doctor, specialty, schedule.getWorkDate(), schedule.getSession(), slot);
                    if (skipped.contains(aiSlot.getSlotId())) {
                        continue;
                    }
                    slotsByDate.computeIfAbsent(schedule.getWorkDate(), ignored -> new ArrayList<>()).add(aiSlot);
                }
            } catch (ApiException ex) {
                log.debug("Skip unavailable AI booking schedule doctorId={} date={} session={} reason={}",
                        doctor.getId(), schedule.getWorkDate(), schedule.getSession(), ex.getMessage());
            }
        }

        return slotsByDate.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .findFirst()
                .map(entry -> entry.getValue().stream()
                        .sorted(this::compareSlots)
                        .limit(Math.max(1, limit))
                        .toList())
                .orElse(List.of());
    }

    private AiAvailableSlotResponse toAiSlot(DoctorProfile doctor,
                                             Specialty specialty,
                                             LocalDate appointmentDate,
                                             BranchSessionType session,
                                             BookableSlotResponse slot) {
        Branch branch = doctor.getBranch();
        String start = formatTime(slot.getStartTime());
        String end = formatTime(slot.getEndTime());
        return AiAvailableSlotResponse.builder()
                .slotId(slotId(branch.getId(), specialty.getId(), doctor.getId(), appointmentDate, session, slot.getStartTime()))
                .doctorId(doctor.getId())
                .doctorName(doctor.getFullName())
                .specialtyId(specialty.getId())
                .specialtyName(specialtyName(specialty))
                .facilityId(branch.getId())
                .facilityName(facilityName(branch))
                .facilityAddress(facilityAddress(branch))
                .appointmentDate(appointmentDate)
                .session(session.name())
                .startTime(start)
                .endTime(end)
                .displayLabel(start)
                .build();
    }

    private Optional<SpecialtyResolution> resolveSpecialtyResolutionForQuestion(String question, String normalized) {
        List<Specialty> specialties = activeSpecialties();
        if (specialties.isEmpty()) {
            return Optional.empty();
        }

        if (hasPediatricContext(normalized) && hasSymptomSignal(normalized)) {
            return Optional.of(new SpecialtyResolution(
                    resolveSpecialtyByCodes(specialties, AssistantSpecialtyGroup.PEDIATRIC).orElse(null),
                    true,
                    AssistantSpecialtyGroup.PEDIATRIC
            ));
        }

        if (looksLikeDirectSpecialtyRequest(normalized)) {
            Optional<AssistantSpecialtyGroup> directGroup = detectDirectSpecialtyGroup(normalized);
            if (directGroup.isPresent()) {
                return Optional.of(new SpecialtyResolution(
                        resolveSpecialtyByCodes(specialties, directGroup.get()).orElse(null),
                        false,
                        null
                ));
            }

            Optional<Specialty> directSpecialty = resolveSpecialtyByExactNameOrCode(specialties, normalized);
            if (directSpecialty.isPresent()) {
                return Optional.of(new SpecialtyResolution(directSpecialty.get(), false, null));
            }
        }

        AssistantSpecialtyGroup symptomGroup = detectSymptomGroup(normalized).orElse(null);
        if (symptomGroup != null) {
            return Optional.of(new SpecialtyResolution(
                    resolveSpecialtyByCodes(specialties, symptomGroup).orElse(null),
                    true,
                    symptomGroup
            ));
        }

        return Optional.empty();
    }

    private PublicAssistantQueryPlan buildPublicAssistantQueryPlan(AiConversationContext context,
                                                                   String question,
                                                                   String normalized,
                                                                   LocalDate datePreference,
                                                                   TimePreference timePreference) {
        List<Branch> activeBranches = activePublicBranches();
        PublicLocationMatch location = resolveLocationMatch(normalized, activeBranches);
        boolean followUpQuestion = isPublicFollowUpQuestion(normalized);
        if (!location.hasLocationSignal() && isBranchFollowUpQuestion(normalized)) {
            location = currentFacilityLocation(context).orElse(location);
        }

        Specialty specialty = resolveSpecialtyResolutionForQuestion(question, normalized)
                .map(SpecialtyResolution::specialty)
                .or(() -> resolveSpecialtyForStandaloneKeyword(normalized))
                .orElse(null);
        String doctorNameQuery = isDoctorGenderQuestion(normalized) ? null : resolveDoctorNameQuery(question, normalized);
        if (specialty != null && isLikelySpecialtyNameQuery(doctorNameQuery, specialty)) {
            doctorNameQuery = null;
        }
        List<DoctorProfile> doctorMatches = findPublicDoctorMatchesByName(normalized, doctorNameQuery);
        PublicServiceMatch serviceMatch = resolvePublicServiceMatch(normalized);
        boolean countQuestion = isCountQuestion(normalized);
        boolean serviceQuestion = isServiceContextQuestion(normalized) && specialty == null;
        boolean branchContext = isBranchOrLocationContextQuestion(normalized, location) || isBranchFollowUpQuestion(normalized);
        boolean doctorContext = !doctorMatches.isEmpty()
                || blankToNull(doctorNameQuery) != null
                || isGeneralDoctorContextQuestion(normalized)
                || isDoctorGenderQuestion(normalized)
                || (isDoctorQuestion(normalized)
                && (!doctorMatches.isEmpty()
                || specialty != null
                || location.hasLocationSignal()
                || hasPublicDoctorBookingIntent(normalized)
                || isExplicitAvailabilityQuestion(normalized, datePreference, timePreference)));
        boolean availabilityQuestion = isExplicitAvailabilityQuestion(normalized, datePreference, timePreference);
        boolean bookingQuestion = hasPublicDoctorBookingIntent(normalized)
                || containsAnyPhrase(normalized, List.of("dat lich", "book", "appointment"));
        boolean infoQuestion = isPublicInfoQuestion(normalized);
        PublicAssistantQueryIntent intent = determinePublicAssistantIntent(
                normalized,
                countQuestion,
                followUpQuestion,
                serviceQuestion,
                branchContext,
                doctorContext,
                availabilityQuestion,
                bookingQuestion,
                doctorNameQuery,
                specialty,
                location
        );
        return new PublicAssistantQueryPlan(
                intent,
                normalized,
                question,
                countQuestion,
                followUpQuestion,
                infoQuestion,
                availabilityQuestion,
                bookingQuestion,
                doctorNameQuery,
                location,
                specialty,
                serviceMatch,
                doctorMatches,
                activeBranches,
                datePreference,
                timePreference
        );
    }

    private PublicAssistantQueryIntent determinePublicAssistantIntent(String normalized,
                                                                      boolean countQuestion,
                                                                      boolean followUpQuestion,
                                                                      boolean serviceQuestion,
                                                                      boolean branchContext,
                                                                      boolean doctorContext,
                                                                      boolean availabilityQuestion,
                                                                      boolean bookingQuestion,
                                                                      String doctorNameQuery,
                                                                      Specialty specialty,
                                                                      PublicLocationMatch location) {
        if (followUpQuestion && isBranchFollowUpQuestion(normalized) && !location.hasLocationSignal()) {
            return PublicAssistantQueryIntent.FOLLOW_UP_BRANCH_INFO;
        }
        if (countQuestion) {
            if (isServiceCountQuestion(normalized)) {
                return PublicAssistantQueryIntent.SERVICE_INFO;
            }
            if (isSpecialtyCountQuestion(normalized)) {
                return PublicAssistantQueryIntent.SPECIALTY_INFO;
            }
            if (isDoctorCountQuestion(normalized) || doctorContext) {
                return PublicAssistantQueryIntent.DOCTOR_COUNT;
            }
            return PublicAssistantQueryIntent.BRANCH_COUNT;
        }
        if (followUpQuestion && isDoctorFollowUpQuestion(normalized)) {
            return availabilityQuestion || bookingQuestion
                    ? PublicAssistantQueryIntent.AVAILABILITY_SEARCH
                    : PublicAssistantQueryIntent.FOLLOW_UP_DOCTOR_INFO;
        }
        if (serviceQuestion) {
            return branchContext || location.hasLocationSignal()
                    ? PublicAssistantQueryIntent.BRANCH_SERVICE_INFO
                    : PublicAssistantQueryIntent.SERVICE_INFO;
        }
        if (specialty != null && branchContext) {
            return PublicAssistantQueryIntent.BRANCH_SPECIALTY_INFO;
        }
        if (doctorContext) {
            if (availabilityQuestion || bookingQuestion) {
                return bookingQuestion ? PublicAssistantQueryIntent.BOOKING_DRAFT : PublicAssistantQueryIntent.AVAILABILITY_SEARCH;
            }
            if (blankToNull(doctorNameQuery) != null) {
                return PublicAssistantQueryIntent.DOCTOR_NAME_LOOKUP;
            }
            if (location.hasLocationSignal()) {
                return PublicAssistantQueryIntent.DOCTOR_LIST_BY_BRANCH;
            }
            return PublicAssistantQueryIntent.DOCTOR_LIST_BY_SPECIALTY_LOCATION;
        }
        if (followUpQuestion && isBranchFollowUpQuestion(normalized)) {
            return PublicAssistantQueryIntent.FOLLOW_UP_BRANCH_INFO;
        }
        if (branchContext) {
            return PublicAssistantQueryIntent.BRANCH_INFO;
        }
        if (specialty != null) {
            return PublicAssistantQueryIntent.SPECIALTY_INFO;
        }
        if (hasControlledSymptomIntent(normalized)) {
            return PublicAssistantQueryIntent.SYMPTOM_GUIDANCE;
        }
        return PublicAssistantQueryIntent.FAQ_OR_FALLBACK;
    }

    private Optional<PublicAssistantResponse> handlePublicCountContext(AiConversationContext context,
                                                                       PublicAssistantQueryPlan plan) {
        if (plan.intent() == PublicAssistantQueryIntent.BRANCH_COUNT) {
            return Optional.of(handlePublicBranchCountContext(context, plan.location()));
        }
        if (plan.intent() == PublicAssistantQueryIntent.DOCTOR_COUNT) {
            return Optional.of(handlePublicDoctorCountContext(context, plan));
        }
        if (plan.intent() == PublicAssistantQueryIntent.SPECIALTY_INFO && isSpecialtyCountQuestion(plan.normalizedText())) {
            return Optional.of(handlePublicSpecialtyCountContext(context, plan.location()));
        }
        if (plan.intent() == PublicAssistantQueryIntent.SERVICE_INFO && isServiceCountQuestion(plan.normalizedText())) {
            return Optional.of(handlePublicServiceCountContext(context, plan.serviceMatch(), plan.location()));
        }
        return Optional.empty();
    }

    private PublicAssistantResponse handlePublicBranchCountContext(AiConversationContext context,
                                                                   PublicLocationMatch location) {
        List<Branch> branches = location.hasLocationSignal()
                ? location.matchedBranches()
                : activePublicBranches();
        if (branches.isEmpty()) {
            return buildResponse(
                    context,
                    "BRANCH_COUNT",
                    "Hiện chưa tìm thấy cơ sở PrimeCare đang hoạt động"
                            + (location.hasLocationSignal() ? " tại " + location.displayLabelOr("khu vực này") : "")
                            + ".",
                    null,
                    List.of(),
                    null,
                    List.of(),
                    false
            );
        }

        updateContextForBranchResults(context, branches);
        String where = location.hasLocationSignal() ? " phù hợp tại " + location.displayLabelOr("khu vực này") : "";
        StringBuilder message = new StringBuilder("PrimeCare hiện có ")
                .append(branches.size())
                .append(" cơ sở đang hoạt động")
                .append(where)
                .append(":\n");
        for (Branch branch : branches.stream().limit(5).toList()) {
            appendBranchLine(message, branch, false);
            message.append('\n');
        }
        message.append("Bạn muốn xem chuyên khoa hoặc bác sĩ tại cơ sở nào?");

        return buildResponse(
                context,
                "BRANCH_COUNT",
                message.toString().trim(),
                null,
                List.of(),
                null,
                branchActions(branches, null),
                false
        );
    }

    private PublicAssistantResponse handlePublicDoctorCountContext(AiConversationContext context,
                                                                   PublicAssistantQueryPlan plan) {
        List<DoctorProfile> doctors = publicDoctorsForPlan(plan);
        if (doctors.isEmpty()) {
            String target = doctorCountTargetText(plan);
            return buildResponse(
                    context,
                    "DOCTOR_COUNT",
                    "Hiện chưa tìm thấy bác sĩ khả dụng" + target + " trên hệ thống PrimeCare.",
                    null,
                    List.of(),
                    null,
                    List.of(),
                    false
            );
        }

        updateContextForDoctorResults(context, doctors, plan.specialty(), "DOCTOR_COUNT");
        StringBuilder message = new StringBuilder(doctorCountPrefix(plan, doctors.size())).append(":\n");
        for (DoctorProfile doctor : doctors.stream().limit(5).toList()) {
            appendDoctorLine(message, doctor, plan.specialty());
        }
        message.append("Bạn muốn xem lịch khám của bác sĩ nào?");

        Specialty actionSpecialty = plan.specialty() != null
                ? plan.specialty()
                : doctors.size() == 1 ? resolveDoctorSpecialty(doctors.get(0), null).orElse(null) : null;
        return buildResponse(
                context,
                "DOCTOR_COUNT",
                message.toString().trim(),
                doctors.size() == 1 && actionSpecialty != null ? suggestedDoctor(doctors.get(0), actionSpecialty) : null,
                List.of(),
                null,
                doctorActions(doctors, actionSpecialty, false),
                false
        );
    }

    private PublicAssistantResponse handlePublicSpecialtyCountContext(AiConversationContext context,
                                                                      PublicLocationMatch location) {
        List<Branch> branches = branchesForLocation(location);
        if (branches.size() == 1) {
            Branch branch = branches.get(0);
            List<String> specialtyNames = publicSpecialtyNamesForBranch(branch.getId(), 100);
            updateContextForBranchResults(context, branches);
            StringBuilder message = new StringBuilder(facilityName(branch))
                    .append(" hiện có ")
                    .append(specialtyNames.size())
                    .append(" chuyên khoa công khai");
            if (!specialtyNames.isEmpty()) {
                message.append(":\n");
                specialtyNames.stream().limit(8).forEach(name -> message.append("- ").append(name).append('\n'));
                message.append("Bạn muốn xem bác sĩ ở chuyên khoa nào?");
            } else {
                message.append(".");
            }
            return buildResponse(context, "SPECIALTY_COUNT", message.toString().trim(), null, List.of(), null, branchActions(branches, null), false);
        }

        List<Specialty> specialties = activeSpecialties();
        StringBuilder message = new StringBuilder("PrimeCare hiện có ")
                .append(specialties.size())
                .append(" chuyên khoa công khai");
        if (!specialties.isEmpty()) {
            message.append(", gồm: ");
            message.append(String.join(", ", specialties.stream().map(this::specialtyName).filter(Objects::nonNull).limit(8).toList()));
            message.append(".");
        } else {
            message.append(".");
        }
        return buildResponse(context, "SPECIALTY_COUNT", message.toString(), null, List.of(), null, List.of(), false);
    }

    private PublicAssistantResponse handlePublicServiceCountContext(AiConversationContext context,
                                                                    PublicServiceMatch serviceMatch,
                                                                    PublicLocationMatch location) {
        List<MedicalService> services = serviceMatch != null && !serviceMatch.matchedServices().isEmpty()
                ? serviceMatch.matchedServices()
                : activePublicMedicalServices();
        String where = location.hasLocationSignal() ? " tại " + location.displayLabelOr("cơ sở này") : "";
        StringBuilder message = new StringBuilder("Danh mục công khai PrimeCare hiện có ")
                .append(services.size())
                .append(" dịch vụ");
        if (!services.isEmpty()) {
            message.append(":\n");
            services.stream().limit(6).forEach(service -> message.append("- ").append(medicalServiceName(service)).append('\n'));
        } else {
            message.append(".");
        }
        message.append("Hệ thống hiện chưa xác nhận phân bổ dịch vụ theo từng cơ sở").append(where).append(".");
        return buildResponse(
                context,
                "SERVICE_COUNT",
                message.toString().trim(),
                null,
                List.of(),
                null,
                List.of(action("Xem dịch vụ y tế", "NAVIGATE", "/medical-services", Map.of())),
                false
        );
    }

    private PublicAssistantResponse handlePublicBranchFollowUp(AiConversationContext context, String normalized) {
        if (context == null || context.getCurrentFacilityId() == null) {
            return clarification(context, "FACILITY_INFO", "Bạn muốn hỏi cơ sở nào ạ?", List.of());
        }
        if (isServiceContextQuestion(normalized)) {
            return handlePublicServiceContext(context, resolvePublicServiceMatch(normalized), currentFacilityLocation(context).orElse(new PublicLocationMatch(null, null, List.of())));
        }
        if (containsAny(normalized, "chuyen khoa gi", "khoa gi", "co khoa gi")) {
            return handlePublicSpecialtyCountContext(context, currentFacilityLocation(context).orElse(new PublicLocationMatch(null, null, List.of())));
        }
        return facilityInfo(context, normalized);
    }

    private Optional<PublicLocationMatch> currentFacilityLocation(AiConversationContext context) {
        if (context == null || context.getCurrentFacilityId() == null) {
            return Optional.empty();
        }
        Branch branch = branchRepository.findById(context.getCurrentFacilityId()).orElse(null);
        if (branch == null || branch.getStatus() != BranchStatus.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(new PublicLocationMatch(facilityName(branch), facilityName(branch), List.of(branch)));
    }

    private List<Branch> branchesForLocation(PublicLocationMatch location) {
        if (location != null && location.hasLocationSignal()) {
            return location.matchedBranches();
        }
        return activePublicBranches();
    }

    private void updateContextForBranchResults(AiConversationContext context, List<Branch> branches) {
        if (context == null || branches == null) {
            return;
        }
        List<Branch> safeBranches = branches.stream()
                .filter(branch -> branch != null && branch.getStatus() == BranchStatus.ACTIVE)
                .toList();
        if (safeBranches.size() == 1) {
            Branch branch = safeBranches.get(0);
            context.setCurrentFacilityId(branch.getId());
            context.setCurrentFacilityName(facilityName(branch));
            context.setCurrentFacilityAddress(facilityAddress(branch));
        } else if (safeBranches.size() > 1) {
            context.setCurrentFacilityId(null);
            context.setCurrentFacilityName(null);
            context.setCurrentFacilityAddress(null);
            context.setCurrentDoctorId(null);
            context.setCurrentDoctorName(null);
            context.setLastAvailableSlots(List.of());
        }
    }

    private void appendBranchLine(StringBuilder message, Branch branch, boolean includeSpecialtySample) {
        message.append("- ").append(facilityName(branch));
        if (blankToNull(facilityAddress(branch)) != null) {
            message.append(" - ").append(facilityAddress(branch));
        }
        if (blankToNull(branch.getPhone()) != null) {
            message.append(" - ĐT: ").append(branch.getPhone().trim());
        }
        if (includeSpecialtySample) {
            List<String> specialties = publicSpecialtyNamesForBranch(branch.getId(), 4);
            if (!specialties.isEmpty()) {
                message.append(". Chuyên khoa: ").append(String.join(", ", specialties));
            }
        }
    }

    private List<DoctorProfile> publicDoctorsForPlan(PublicAssistantQueryPlan plan) {
        List<DoctorProfile> doctors;
        if (blankToNull(plan.doctorNameQuery()) != null) {
            doctors = plan.doctorMatches();
        } else if (plan.specialty() != null) {
            doctors = findPublicDoctorsBySpecialtyAndLocation(plan.specialty(), plan.location());
        } else if (plan.location().hasLocationSignal()) {
            doctors = findPublicDoctorsByLocation(plan.location());
        } else {
            doctors = publicDoctorCandidates();
        }

        if (plan.specialty() != null) {
            Long specialtyId = plan.specialty().getId();
            doctors = doctors.stream()
                    .filter(doctor -> publicValidSpecialties(doctor).stream().anyMatch(specialty -> specialtyId.equals(specialty.getId())))
                    .toList();
        }
        if (plan.location().hasLocationSignal()) {
            doctors = doctors.stream()
                    .filter(doctor -> doctor.getBranch() != null && plan.location().matches(doctor.getBranch()))
                    .toList();
        }
        return doctors.stream()
                .filter(doctor -> doctor != null && !publicValidSpecialties(doctor).isEmpty())
                .sorted(Comparator.comparing(DoctorProfile::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(20)
                .toList();
    }

    private void updateContextForDoctorResults(AiConversationContext context,
                                               List<DoctorProfile> doctors,
                                               Specialty specialty,
                                               String intent) {
        if (context == null || doctors == null || doctors.isEmpty()) {
            return;
        }
        if (doctors.size() == 1) {
            Specialty effectiveSpecialty = specialty != null
                    ? specialty
                    : resolveDoctorSpecialty(doctors.get(0), null).orElse(null);
            if (effectiveSpecialty != null) {
                updateContextForDoctor(context, suggestedDoctor(doctors.get(0), effectiveSpecialty), List.of(), intent);
            }
            return;
        }
        context.setCurrentDoctorId(null);
        context.setCurrentDoctorName(null);
        context.setLastSuggestedDoctors(doctors.stream()
                .limit(5)
                .map(doctor -> {
                    Specialty effectiveSpecialty = specialty != null
                            ? specialty
                            : resolveDoctorSpecialty(doctor, null).orElse(null);
                    return effectiveSpecialty == null ? null : suggestedDoctor(doctor, effectiveSpecialty);
                })
                .filter(Objects::nonNull)
                .toList());
    }

    private void appendDoctorLine(StringBuilder message, DoctorProfile doctor, Specialty specialty) {
        Specialty displaySpecialty = specialty != null
                ? specialty
                : resolveDoctorSpecialty(doctor, null).orElse(null);
        message.append("- ").append(doctor.getFullName());
        if (displaySpecialty != null) {
            message.append(" - ").append(specialtyName(displaySpecialty));
        }
        if (doctor.getBranch() != null) {
            message.append(" tại ").append(facilityName(doctor.getBranch()));
        }
        message.append('\n');
    }

    private String doctorCountPrefix(PublicAssistantQueryPlan plan, int count) {
        String target = doctorCountTargetText(plan);
        if (plan.location().hasLocationSignal() && plan.location().matchedBranches().size() == 1) {
            return facilityName(plan.location().matchedBranches().get(0)) + " hiện có " + count + " bác sĩ khả dụng" + doctorSpecialtyText(plan);
        }
        return "PrimeCare hiện có " + count + " bác sĩ khả dụng" + doctorSpecialtyText(plan) + target;
    }

    private String doctorCountTargetText(PublicAssistantQueryPlan plan) {
        if (blankToNull(plan.doctorNameQuery()) != null) {
            return " có tên '" + displayDoctorNameQuery(plan.doctorNameQuery()) + "'";
        }
        if (plan.location().hasLocationSignal() && plan.location().matchedBranches().size() != 1) {
            return " tại " + plan.location().displayLabelOr("khu vực này");
        }
        return "";
    }

    private String doctorSpecialtyText(PublicAssistantQueryPlan plan) {
        return plan.specialty() == null ? "" : " " + specialtyName(plan.specialty());
    }

    private Optional<AssistantSpecialtyGroup> detectSymptomGroup(String normalized) {
        AssistantSpecialtyGroup bestGroup = null;
        int bestScore = 0;
        for (AssistantSpecialtyGroup group : AssistantSpecialtyGroup.values()) {
            if (group == AssistantSpecialtyGroup.PEDIATRIC) {
                continue;
            }
            for (String keyword : group.symptomKeywords()) {
                String normalizedKeyword = searchable(keyword);
                if (!normalizedKeyword.isBlank() && containsPhrase(normalized, normalizedKeyword)) {
                    int score = normalizedKeyword.length();
                    if (score > bestScore) {
                        bestScore = score;
                        bestGroup = group;
                    }
                }
            }
        }
        return Optional.ofNullable(bestGroup);
    }

    private Optional<AssistantSpecialtyGroup> detectDirectSpecialtyGroup(String normalized) {
        AssistantSpecialtyGroup bestGroup = null;
        int bestScore = 0;
        for (AssistantSpecialtyGroup group : AssistantSpecialtyGroup.values()) {
            for (String alias : group.directAliases()) {
                String normalizedAlias = searchable(alias);
                if (!normalizedAlias.isBlank() && containsPhrase(normalized, normalizedAlias)) {
                    int score = normalizedAlias.length();
                    if (score > bestScore) {
                        bestScore = score;
                        bestGroup = group;
                    }
                }
            }
        }
        return Optional.ofNullable(bestGroup);
    }

    private Optional<Specialty> resolveSpecialtyByCodes(List<Specialty> specialties, AssistantSpecialtyGroup group) {
        for (String code : group.preferredCodes()) {
            Optional<Specialty> matched = findSpecialtyByCode(specialties, code);
            if (matched.isPresent()) {
                return matched;
            }
        }
        for (String code : group.fallbackCodes()) {
            Optional<Specialty> matched = findSpecialtyByCode(specialties, code);
            if (matched.isPresent()) {
                return matched;
            }
        }
        return Optional.empty();
    }

    private Optional<Specialty> findSpecialtyByCode(List<Specialty> specialties, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return specialties.stream()
                .filter(specialty -> specialty != null && specialty.getCode() != null)
                .filter(specialty -> code.equalsIgnoreCase(specialty.getCode().trim()))
                .findFirst();
    }

    private Optional<Specialty> resolveSpecialtyByExactNameOrCode(List<Specialty> specialties, String normalized) {
        Specialty best = null;
        int bestScore = 0;
        for (Specialty specialty : specialties) {
            String[] candidates = {
                    specialty.getCode(),
                    specialty.getNameVn(),
                    specialty.getNameEn()
            };
            for (String candidate : candidates) {
                String normalizedCandidate = searchable(candidate);
                if (!normalizedCandidate.isBlank() && containsPhrase(normalized, normalizedCandidate)) {
                    int score = normalizedCandidate.length();
                    if (score > bestScore) {
                        bestScore = score;
                        best = specialty;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<Specialty> resolveSpecialtyForStandaloneKeyword(String normalized) {
        List<Specialty> specialties = activeSpecialties();
        Optional<AssistantSpecialtyGroup> directGroup = detectDirectSpecialtyGroup(normalized);
        if (directGroup.isPresent()) {
            Optional<Specialty> byCode = resolveSpecialtyByCodes(specialties, directGroup.get());
            if (byCode.isPresent()) {
                return byCode;
            }
        }
        Optional<Specialty> exact = resolveSpecialtyByExactNameOrCode(specialties, normalized);
        if (exact.isPresent()) {
            return exact;
        }
        return detectSymptomGroup(normalized).flatMap(group -> resolveSpecialtyByCodes(specialties, group));
    }

    private boolean looksLikeDirectSpecialtyRequest(String normalized) {
        return containsAnyPhrase(normalized, List.of(
                "kham",
                "muon kham",
                "chuyen khoa",
                "khoa",
                "bac si",
                "doctor",
                "specialty",
                "co bac si",
                "de xuat bac si"
        ));
    }

    private boolean hasPediatricContext(String normalized) {
        return containsAnyPhrase(normalized, List.of(
                "con toi",
                "be nha toi",
                "be",
                "tre",
                "tre em",
                "em be",
                "chau",
                "con em",
                "be trai",
                "be gai",
                "tre bi",
                "be bi"
        ));
    }

    private boolean hasSymptomSignal(String normalized) {
        if (containsAnyPhrase(normalized, List.of("toi bi", "toi hay", "trieu chung", "dau", "ngua", "sot", "ho", "kho tho", "noi man"))) {
            return true;
        }
        for (AssistantSpecialtyGroup group : AssistantSpecialtyGroup.values()) {
            if (containsAnyPhrase(normalized, group.symptomKeywords())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSymptomQuestion(String normalized) {
        return hasSymptomSignal(normalized);
    }

    private boolean containsAnyPhrase(String normalized, List<String> phrases) {
        if (normalized == null || normalized.isBlank() || phrases == null || phrases.isEmpty()) {
            return false;
        }
        for (String phrase : phrases) {
            if (containsPhrase(normalized, phrase)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPhrase(String normalized, String phrase) {
        String normalizedPhrase = searchable(phrase);
        if (normalized == null || normalized.isBlank() || normalizedPhrase.isBlank()) {
            return false;
        }
        return (" " + normalized + " ").contains(" " + normalizedPhrase + " ");
    }

    private Optional<DoctorProfile> resolveDoctor(AiConversationContext context, String question, String normalized) {
        if (containsAny(normalized, "bac si nay", "bs nay", "doctor nay") && context.getCurrentDoctorId() != null) {
            return loadDoctor(context.getCurrentDoctorId());
        }
        Optional<DoctorProfile> doctorByName = findDoctorByName(question);
        if (doctorByName.isPresent()) {
            return doctorByName;
        }
        if (context.getCurrentDoctorId() != null && containsAny(normalized, "lich", "con", "gio", "kham", "o dau", "chuyen khoa")) {
            return loadDoctor(context.getCurrentDoctorId());
        }
        return Optional.empty();
    }

    private Optional<DoctorProfile> findDoctorByName(String question) {
        String normalized = searchable(question);
        if (!containsAny(normalized, "bac si", "bs ", "doctor")) {
            return Optional.empty();
        }
        var doctorPage = doctorProfileRepository.search(
                null,
                null,
                null,
                DoctorStatus.ACTIVE,
                PageRequest.of(0, 100, Sort.by("createdAt").descending())
        );
        List<DoctorProfile> doctors = doctorPage == null ? List.of() : doctorPage.getContent();

        DoctorProfile best = null;
        int bestScore = 0;
        for (DoctorProfile doctor : doctors) {
            if (publicValidSpecialties(doctor).isEmpty()) {
                continue;
            }
            String name = searchable(doctor.getFullName());
            int score = containsPhrase(normalized, name) ? 100 : 0;
            for (String token : name.split(" ")) {
                if (token.length() >= 2 && containsPhrase(normalized, token)) {
                    score += 5;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = doctor;
            }
        }
        return bestScore >= 10 ? Optional.of(best) : Optional.empty();
    }

    private List<DoctorProfile> findDoctorsBySpecialty(Long specialtyId, Set<Long> excludedDoctorIds) {
        Set<Long> excluded = excludedDoctorIds == null ? Set.of() : excludedDoctorIds;
        return doctorProfileRepository.search(
                        null,
                        specialtyId,
                        null,
                        DoctorStatus.ACTIVE,
                        PageRequest.of(0, 30, Sort.by("createdAt").descending())
                ).getContent()
                .stream()
                .filter(doctor -> doctor.getId() != null && !excluded.contains(doctor.getId()))
                .filter(doctor -> doctor.getStatus() == DoctorStatus.ACTIVE)
                .filter(doctor -> doctor.getBranch() != null && doctor.getBranch().getStatus() == BranchStatus.ACTIVE)
                .toList();
    }

    private List<Specialty> activeSpecialties() {
        var page = specialtyRepository.findAllByStatus("ACTIVE", PageRequest.of(0, 200, Sort.by("nameVn").ascending()));
        return page == null ? List.of() : page.getContent();
    }

    private Optional<DoctorProfile> loadDoctor(Long doctorId) {
        return doctorId == null ? Optional.empty() : doctorProfileRepository.findPublicBookableById(doctorId);
    }

    private Optional<Specialty> loadSpecialty(Long specialtyId) {
        return specialtyId == null ? Optional.empty() : specialtyRepository.findById(specialtyId);
    }

    private Optional<Specialty> resolveDoctorSpecialty(DoctorProfile doctor, Long preferredSpecialtyId) {
        if (doctor == null || doctor.getDoctorSpecialties() == null) {
            return Optional.empty();
        }
        List<Specialty> publicSpecialties = publicValidSpecialties(doctor);
        if (publicSpecialties.isEmpty()) {
            return Optional.empty();
        }
        if (preferredSpecialtyId != null) {
            Optional<Specialty> preferred = publicSpecialties.stream()
                    .filter(specialty -> preferredSpecialtyId.equals(specialty.getId()))
                    .findFirst();
            if (preferred.isPresent()) {
                return preferred;
            }
        }
        return publicSpecialties.stream().findFirst();
    }

    private SlotSelectionMatch matchSlotSelection(String question, String normalized, AiConversationContext context) {
        List<AiAvailableSlotResponse> slots = context.getLastAvailableSlots();
        if (slots == null || slots.isEmpty()) {
            return new SlotSelectionMatch(false, null, false);
        }

        Integer ordinal = parseOrdinalSelection(normalized);
        if (ordinal != null) {
            if (ordinal >= 0 && ordinal < slots.size()) {
                return new SlotSelectionMatch(true, slots.get(ordinal), false);
            }
            return new SlotSelectionMatch(true, null, false);
        }

        LocalTime requestedTime = parseClockTime(question, normalized).orElse(null);
        boolean explicitSelection = containsAnyPhrase(normalized, List.of("chon", "toi chon", "lay lich", "dat ca", "dat lich"))
                || (containsPhrase(normalized, "toi muon") && parseTimePreference(question, normalized) == null);
        if (requestedTime != null && explicitSelection) {
            List<AiAvailableSlotResponse> matches = slots.stream()
                    .filter(slot -> requestedTime.equals(parseSlotTime(slot.getStartTime())))
                    .toList();
            if (matches.size() == 1) {
                return new SlotSelectionMatch(true, matches.get(0), false);
            }
            return new SlotSelectionMatch(true, null, matches.size() > 1);
        }
        return new SlotSelectionMatch(false, null, false);
    }

    private Integer parseOrdinalSelection(String normalized) {
        if (containsAny(normalized, "ca dau", "dau tien", "slot dau")) {
            return 0;
        }
        if (containsAny(normalized, "ca thu hai", "slot thu hai")) {
            return 1;
        }
        if (containsAny(normalized, "ca thu ba", "slot thu ba")) {
            return 2;
        }
        Matcher matcher = Pattern.compile("\\b(?:ca|slot)\\s*(?:thu\\s*)?(\\d+)\\b").matcher(normalized);
        if (matcher.find()) {
            try {
                return Math.max(0, Integer.parseInt(matcher.group(1)) - 1);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Optional<LocalTime> parseClockTime(String original, String normalized) {
        Matcher colonOrH = Pattern.compile("\\b(\\d{1,2})\\s*(?:[:hH])\\s*(\\d{0,2})\\b").matcher(original == null ? "" : original);
        if (colonOrH.find()) {
            return buildTime(colonOrH.group(1), colonOrH.group(2));
        }
        Matcher gio = Pattern.compile("\\b(\\d{1,2})\\s*(?:gio|h)\\s*(\\d{0,2})\\b").matcher(normalized == null ? "" : normalized);
        if (gio.find()) {
            return buildTime(gio.group(1), gio.group(2));
        }
        return Optional.empty();
    }

    private Optional<LocalTime> buildTime(String hourValue, String minuteValue) {
        try {
            int hour = Integer.parseInt(hourValue);
            int minute = minuteValue == null || minuteValue.isBlank() ? 0 : Integer.parseInt(minuteValue);
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return Optional.of(LocalTime.of(hour, minute));
            }
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private TimePreference parseTimePreference(String question, String normalized) {
        if (containsAny(normalized, "buoi chieu", "chieu")) {
            return new TimePreference("AFTERNOON", LocalTime.NOON, null);
        }
        if (containsAny(normalized, "buoi sang", "sang")) {
            return new TimePreference("MORNING", null, LocalTime.NOON);
        }
        LocalTime requested = parseClockTime(question, normalized).orElse(null);
        if (requested == null) {
            return null;
        }
        if (containsAny(normalized, "sau", "after")) {
            return new TimePreference("AFTER_" + formatTime(requested), requested, null);
        }
        if (containsAny(normalized, "truoc", "before")) {
            return new TimePreference("BEFORE_" + formatTime(requested), null, requested);
        }
        return null;
    }

    private TimePreference parseStoredTimePreference(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        if ("AFTERNOON".equals(label)) {
            return new TimePreference("AFTERNOON", LocalTime.NOON, null);
        }
        if ("MORNING".equals(label)) {
            return new TimePreference("MORNING", null, LocalTime.NOON);
        }
        if (label.startsWith("AFTER_")) {
            return new TimePreference(label, parseSlotTime(label.substring("AFTER_".length())), null);
        }
        if (label.startsWith("BEFORE_")) {
            return new TimePreference(label, null, parseSlotTime(label.substring("BEFORE_".length())));
        }
        return null;
    }

    private boolean matchesTimePreference(LocalTime startTime, TimePreference preference) {
        if (startTime == null || preference == null) {
            return true;
        }
        if (preference.notBefore() != null && startTime.isBefore(preference.notBefore())) {
            return false;
        }
        return preference.before() == null || startTime.isBefore(preference.before());
    }

    private void applySchedulePreference(AiConversationContext context, LocalDate fromDate, TimePreference timePreference) {
        if (context == null) {
            return;
        }
        if (fromDate != null) {
            context.setPreferredFromDate(fromDate);
            context.setPreferredDate(fromDate);
            context.setPreferredWeekday(fromDate.getDayOfWeek().name());
        }
        if (timePreference != null) {
            context.setUserTimePreference(timePreference.label());
            context.setPreferredAfterTime(formatTime(timePreference.notBefore()));
            context.setPreferredBeforeTime(formatTime(timePreference.before()));
        }
    }

    private Optional<LocalDate> parseDateKeyword(String dateKeyword) {
        return parseDatePreference(dateKeyword, searchable(dateKeyword));
    }

    private Optional<LocalDate> parseDatePreference(String original, String normalized) {
        String safeOriginal = original == null ? "" : original.trim();
        String safeNormalized = normalized == null || normalized.isBlank() ? searchable(original) : normalized;
        if (safeNormalized.isBlank()) {
            return Optional.empty();
        }
        if (containsAnyPhrase(safeNormalized, List.of("hom nay", "today"))) {
            return Optional.of(LocalDate.now());
        }
        if (containsAnyPhrase(safeNormalized, List.of("ngay mai", "sang mai", "chieu mai", "toi mai", "mai", "tomorrow"))) {
            return Optional.of(LocalDate.now().plusDays(1));
        }
        if (containsAnyPhrase(safeNormalized, List.of("ngay kia", "day after"))) {
            return Optional.of(LocalDate.now().plusDays(2));
        }
        Optional<DayOfWeek> requestedDay = detectDayOfWeek(safeNormalized);
        if (requestedDay.isPresent()) {
            LocalDate base = containsPhrase(safeNormalized, "tuan sau")
                    ? LocalDate.now().plusWeeks(1)
                    : LocalDate.now();
            return Optional.of(base.with(TemporalAdjusters.nextOrSame(requestedDay.get())));
        }
        if (containsAnyPhrase(safeNormalized, List.of("tuan nay", "this week"))) {
            return Optional.of(LocalDate.now());
        }
        if (containsAnyPhrase(safeNormalized, List.of("tuan sau", "next week"))) {
            return Optional.of(LocalDate.now().plusWeeks(1));
        }

        Matcher isoMatcher = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b").matcher(safeOriginal);
        if (isoMatcher.find()) {
            try {
                return Optional.of(LocalDate.parse(isoMatcher.group(), DateTimeFormatter.ISO_LOCAL_DATE));
            } catch (DateTimeParseException ignored) {
            }
        }

        Matcher dmyMatcher = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?\\b").matcher(safeOriginal);
        if (dmyMatcher.find()) {
            try {
                int day = Integer.parseInt(dmyMatcher.group(1));
                int month = Integer.parseInt(dmyMatcher.group(2));
                int year = dmyMatcher.group(3) == null ? LocalDate.now().getYear() : Integer.parseInt(dmyMatcher.group(3));
                LocalDate parsed = LocalDate.of(year, month, day);
                if (dmyMatcher.group(3) == null && parsed.isBefore(LocalDate.now())) {
                    parsed = parsed.plusYears(1);
                }
                return Optional.of(parsed);
            } catch (RuntimeException ignored) {
            }
        }

        try {
            return Optional.of(LocalDate.parse(safeOriginal, DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Optional.of(LocalDate.parse(safeOriginal, DATE_FORMATTER));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private Optional<DayOfWeek> detectDayOfWeek(String normalized) {
        if (containsAnyPhrase(normalized, List.of("thu 2", "thu hai", "monday"))) {
            return Optional.of(DayOfWeek.MONDAY);
        }
        if (containsAnyPhrase(normalized, List.of("thu 3", "thu ba", "tuesday"))) {
            return Optional.of(DayOfWeek.TUESDAY);
        }
        if (containsAnyPhrase(normalized, List.of("thu 4", "thu tu", "wednesday"))) {
            return Optional.of(DayOfWeek.WEDNESDAY);
        }
        if (containsAnyPhrase(normalized, List.of("thu 5", "thu nam", "thursday"))) {
            return Optional.of(DayOfWeek.THURSDAY);
        }
        if (containsAnyPhrase(normalized, List.of("thu 6", "thu sau", "friday"))) {
            return Optional.of(DayOfWeek.FRIDAY);
        }
        if (containsAnyPhrase(normalized, List.of("thu 7", "thu bay", "saturday"))) {
            return Optional.of(DayOfWeek.SATURDAY);
        }
        if (containsAnyPhrase(normalized, List.of("chu nhat", "sunday"))) {
            return Optional.of(DayOfWeek.SUNDAY);
        }
        return Optional.empty();
    }

    private Optional<PublicAssistantResponse> tryHandlePublicCareContext(AiConversationContext context,
                                                                         String question,
                                                                         String normalized,
                                                                         LocalDate datePreference,
                                                                         TimePreference timePreference) {
        if (isPublicContextBlockedSystemQuestion(normalized)) {
            return Optional.empty();
        }

        PublicAssistantQueryPlan plan = buildPublicAssistantQueryPlan(
                context,
                question,
                normalized,
                datePreference,
                timePreference
        );
        boolean explicitDoctorContext = blankToNull(plan.doctorNameQuery()) != null
                || isGeneralDoctorContextQuestion(normalized)
                || isDoctorGenderQuestion(normalized);
        if (plan.intent() == PublicAssistantQueryIntent.FAQ_OR_FALLBACK
                && !looksLikePublicCareContext(normalized)
                && plan.doctorMatches().isEmpty()
                && !explicitDoctorContext) {
            return Optional.empty();
        }

        if (plan.countQuestion()) {
            Optional<PublicAssistantResponse> countResponse = handlePublicCountContext(context, plan);
            if (countResponse.isPresent()) {
                return countResponse;
            }
        }

        if (plan.intent() == PublicAssistantQueryIntent.FOLLOW_UP_DOCTOR_INFO) {
            Optional<DoctorProfile> doctor = loadDoctor(context.getCurrentDoctorId());
            if (doctor.isPresent()) {
                return Optional.of(handleDoctorQuestion(context, doctor.get(), normalized));
            }
            return Optional.of(clarification(context, "DOCTOR_SEARCH", "Bạn muốn hỏi bác sĩ nào ạ?", List.of()));
        }

        if (plan.intent() == PublicAssistantQueryIntent.FOLLOW_UP_BRANCH_INFO) {
            return Optional.of(handlePublicBranchFollowUp(context, normalized));
        }

        if (plan.intent() == PublicAssistantQueryIntent.DOCTOR_NAME_LOOKUP
                || plan.intent() == PublicAssistantQueryIntent.DOCTOR_LIST_BY_BRANCH
                || plan.intent() == PublicAssistantQueryIntent.DOCTOR_LIST_BY_SPECIALTY_LOCATION
                || plan.intent() == PublicAssistantQueryIntent.AVAILABILITY_SEARCH
                || plan.intent() == PublicAssistantQueryIntent.BOOKING_DRAFT) {
            return Optional.of(handlePublicDoctorContext(
                    context,
                    question,
                    normalized,
                    plan.doctorMatches(),
                    plan.doctorNameQuery(),
                    plan.specialty(),
                    plan.location(),
                    datePreference,
                    timePreference
            ));
        }

        if (plan.intent() == PublicAssistantQueryIntent.BRANCH_SPECIALTY_INFO && plan.availabilityQuestion()) {
            return Optional.of(handlePublicBranchSpecialtyAvailability(
                    context,
                    plan.specialty(),
                    plan.location(),
                    datePreference,
                    timePreference
            ));
        }

        if (plan.intent() == PublicAssistantQueryIntent.BRANCH_SPECIALTY_INFO) {
            return Optional.of(handlePublicBranchSpecialtyContext(context, plan.specialty(), plan.location()));
        }

        if (plan.intent() == PublicAssistantQueryIntent.BRANCH_SERVICE_INFO
                || plan.intent() == PublicAssistantQueryIntent.SERVICE_INFO) {
            return Optional.of(handlePublicServiceContext(context, plan.serviceMatch(), plan.location()));
        }

        if (plan.intent() == PublicAssistantQueryIntent.BRANCH_INFO) {
            return Optional.of(handlePublicBranchLocationContext(context, plan.location()));
        }

        return Optional.empty();
    }

    private boolean looksLikePublicCareContext(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        String padded = " " + normalized + " ";
        if (isCountQuestion(normalized) || isPublicFollowUpQuestion(normalized)) {
            return true;
        }
        if (isServiceContextQuestion(normalized)) {
            return true;
        }
        return containsAny(normalized,
                "chi nhanh",
                "co so",
                "phong kham",
                "dia chi",
                "so dien thoai",
                "primecare co o",
                "primecare co tai",
                "khu vuc",
                "gan day",
                "gan toi",
                "kham o dau",
                "thuoc chi nhanh",
                "lam o primecare",
                "tim bac si",
                "danh sach bac si",
                "nhung bac si",
                "bao nhieu bac si",
                "bac si hien co",
                "co bac si nao",
                "co ai ten")
                || (isDoctorQuestion(normalized) && (containsAny(padded, " o ", " tai ") || containsAny(normalized, "co so", "chi nhanh", "ten ", "lich", "dat lich")))
                || (containsAny(padded, " o ", " tai ") && (hasDirectSpecialtySignal(normalized) || isServiceContextQuestion(normalized)));
    }

    private boolean isCountQuestion(String normalized) {
        return containsAnyPhrase(normalized, List.of(
                "bao nhieu",
                "co bao nhieu",
                "may",
                "so luong",
                "tong cong",
                "dem"
        ));
    }

    private boolean isDoctorCountQuestion(String normalized) {
        return isCountQuestion(normalized) && isDoctorQuestion(normalized);
    }

    private boolean isSpecialtyCountQuestion(String normalized) {
        return isCountQuestion(normalized)
                && containsAnyPhrase(normalized, List.of("chuyen khoa", "khoa"));
    }

    private boolean isServiceCountQuestion(String normalized) {
        return isCountQuestion(normalized)
                && containsAnyPhrase(normalized, List.of("dich vu", "xet nghiem", "sieu am", "chup"));
    }

    private boolean isPublicFollowUpQuestion(String normalized) {
        return isBranchFollowUpQuestion(normalized)
                || isDoctorFollowUpQuestion(normalized)
                || containsAnyPhrase(normalized, List.of("xem them", "doi bac si", "chuyen khoa nay"));
    }

    private boolean isBranchFollowUpQuestion(String normalized) {
        return containsAnyPhrase(normalized, List.of(
                "co so nay",
                "chi nhanh nay",
                "phong kham nay",
                "co so do",
                "chi nhanh do",
                "o do",
                "tai do",
                "noi nay",
                "dia chi o dau",
                "dia chi co so",
                "so dien thoai co so",
                "so dien thoai chi nhanh"
        ));
    }

    private boolean isDoctorFollowUpQuestion(String normalized) {
        return containsAnyPhrase(normalized, List.of(
                "bac si nay",
                "bs nay",
                "doctor nay",
                "bac si do",
                "nguoi nay",
                "nguoi do"
        ));
    }

    private boolean isPublicInfoQuestion(String normalized) {
        return containsAnyPhrase(normalized, List.of(
                "o dau",
                "dia chi",
                "so dien thoai",
                "bao nhieu",
                "co khong",
                "co chuyen khoa",
                "co dich vu",
                "danh sach",
                "thuoc co so nao",
                "thuoc chi nhanh nao",
                "kham o dau",
                "chuyen khoa gi"
        ));
    }

    private boolean isExplicitAvailabilityQuestion(String normalized, LocalDate datePreference, TimePreference timePreference) {
        if (containsAnyPhrase(normalized, List.of(
                "con lich",
                "co lich",
                "lich kham",
                "lich trong",
                "lich gan nhat",
                "tim lich",
                "slot",
                "ca kham",
                "con ca",
                "co ca",
                "som nhat",
                "gan nhat",
                "trong khong"
        ))) {
            return true;
        }
        if (hasPublicDoctorBookingIntent(normalized)) {
            return true;
        }
        return (datePreference != null || timePreference != null)
                && (isDoctorQuestion(normalized)
                || hasDirectSpecialtySignal(normalized)
                || containsAnyPhrase(normalized, List.of("muon kham", "can kham", "kham som")));
    }

    private boolean isServiceListQuestion(String normalized) {
        return containsAnyPhrase(normalized, List.of(
                "dich vu gi",
                "co dich vu gi",
                "nhung dich vu nao",
                "danh sach dich vu",
                "gi"
        ));
    }

    private boolean isPublicContextBlockedSystemQuestion(String normalized) {
        if (containsAnyPhrase(normalized, List.of("so dien thoai"))
                && containsAnyPhrase(normalized, List.of("chi nhanh", "co so", "phong kham", "primecare"))) {
            return false;
        }
        return containsAnyPhrase(normalized, List.of(
                "nha thuoc",
                "don thuoc",
                "ke don",
                "ho so",
                "ho so benh an",
                "ket qua",
                "tra cuu",
                "otp",
                "mat khau",
                "dang nhap",
                "dang ky tai khoan",
                "tai khoan",
                "ho tro tai khoan",
                "so dien thoai",
                "cap nhat email",
                "cap nhat so dien thoai",
                "bao hiem",
                "thanh toan",
                "trang chu"
        ));
    }

    private boolean isBranchOrLocationContextQuestion(String normalized, PublicLocationMatch location) {
        return (location != null && location.hasLocationSignal())
                || containsAny(normalized,
                "chi nhanh",
                "co so",
                "phong kham",
                "dia chi",
                "so dien thoai",
                "primecare co o",
                "primecare co tai",
                "khu vuc",
                "gan day",
                "gan toi");
    }

    private List<Branch> activePublicBranches() {
        var branchPage = branchRepository.findAllByStatus(
                        BranchStatus.ACTIVE,
                        PageRequest.of(0, 100, Sort.by("nameVn").ascending())
                );
        List<Branch> branches = branchPage == null ? List.of() : branchPage.getContent();
        return branches
                .stream()
                .filter(branch -> branch != null && branch.getStatus() == BranchStatus.ACTIVE)
                .toList();
    }

    private PublicAssistantResponse handlePublicBranchLocationContext(AiConversationContext context,
                                                                     PublicLocationMatch location) {
        List<Branch> matchedBranches = location.hasLocationSignal()
                ? location.matchedBranches()
                : activePublicBranches();
        if (matchedBranches.isEmpty()) {
            return buildResponse(
                    context,
                    "BRANCH_SEARCH",
                    "Hiện chưa tìm thấy cơ sở phù hợp tại " + location.displayLabelOr("khu vực này") + ".",
                    null,
                    List.of(),
                    null,
                    List.of(),
                    false
            );
        }

        updateContextForBranchResults(context, matchedBranches);
        StringBuilder message = new StringBuilder("PrimeCare hiện có các cơ sở phù hợp");
        if (location.hasLocationSignal()) {
            message.append(" tại ").append(location.displayLabelOr("khu vực này"));
        }
        message.append(":\n");
        for (Branch branch : matchedBranches.stream().limit(5).toList()) {
            appendBranchLine(message, branch, false);
            List<String> specialties = publicSpecialtyNamesForBranch(branch.getId(), 4);
            if (!specialties.isEmpty()) {
                message.append(". Chuyên khoa: ").append(String.join(", ", specialties));
            }
        }
        message.append("Bạn muốn xem bác sĩ hoặc chuyên khoa tại cơ sở nào?");

        return buildResponse(
                context,
                "BRANCH_SEARCH",
                message.toString().trim(),
                null,
                List.of(),
                null,
                branchActions(matchedBranches, null),
                false
        );
    }

    private PublicAssistantResponse handlePublicBranchSpecialtyContext(AiConversationContext context,
                                                                      Specialty specialty,
                                                                      PublicLocationMatch location) {
        List<BranchSpecialty> matches = activeBranchSpecialtiesFor(specialty, location);
        String specialtyName = specialtyName(specialty);
        if (matches.isEmpty()) {
            String where = location.hasLocationSignal() ? " tại " + location.displayLabelOr("khu vực này") : "";
            return buildResponse(
                    context,
                    "BRANCH_SPECIALTY_SEARCH",
                    "Hiện chưa tìm thấy cơ sở phù hợp" + where + " có chuyên khoa " + specialtyName + ".",
                    null,
                    List.of(),
                    null,
                    List.of(),
                    false
            );
        }

        String where = location.hasLocationSignal() ? " tại " + location.displayLabelOr("khu vực này") : "";
        List<Branch> matchedBranches = matches.stream().map(BranchSpecialty::getBranch).filter(Objects::nonNull).toList();
        updateContextForBranchResults(context, matchedBranches);
        StringBuilder message = new StringBuilder("PrimeCare hiện có cơ sở phù hợp");
        message.append(where).append(" có chuyên khoa ").append(specialtyName).append(":\n");
        for (BranchSpecialty branchSpecialty : matches.stream().limit(5).toList()) {
            Branch branch = branchSpecialty.getBranch();
            List<DoctorProfile> doctors = doctorProfileRepository.findActiveByBranchAndSpecialty(branch.getId(), specialty.getId())
                    .stream()
                    .filter(doctor -> !publicValidSpecialties(doctor).isEmpty())
                    .toList();
            message.append("- ").append(facilityName(branch));
            if (blankToNull(facilityAddress(branch)) != null) {
                message.append(" - ").append(facilityAddress(branch));
            }
            if (blankToNull(branch.getPhone()) != null) {
                message.append(" - ĐT: ").append(branch.getPhone().trim());
            }
            if (!doctors.isEmpty()) {
                message.append(". Có ").append(doctors.size()).append(" bác sĩ công khai");
                String sample = doctors.stream()
                        .map(DoctorProfile::getFullName)
                        .filter(Objects::nonNull)
                        .limit(2)
                        .reduce((left, right) -> left + ", " + right)
                        .orElse(null);
                if (sample != null) {
                    message.append(" như ").append(sample);
                }
            } else if (matches.size() == 1) {
                message.append(". Có chuyên khoa ").append(specialtyName)
                        .append(", nhưng hiện chưa tìm thấy bác sĩ khả dụng để đặt lịch");
            }
            message.append('\n');
        }
        message.append("Bạn có thể chọn cơ sở hoặc để tôi gợi ý lịch gần nhất.");

        return buildResponse(
                context,
                "BRANCH_SPECIALTY_SEARCH",
                message.toString().trim(),
                null,
                List.of(),
                null,
                branchActions(matchedBranches, specialty, false),
                false
        );
    }

    private PublicAssistantResponse handlePublicBranchSpecialtyAvailability(AiConversationContext context,
                                                                           Specialty specialty,
                                                                           PublicLocationMatch location,
                                                                           LocalDate datePreference,
                                                                           TimePreference timePreference) {
        List<BranchSpecialty> matches = activeBranchSpecialtiesFor(specialty, location);
        if (matches.isEmpty()) {
            String where = location.hasLocationSignal() ? " tại " + location.displayLabelOr("khu vực này") : "";
            return buildResponse(
                    context,
                    "AVAILABILITY_SEARCH",
                    "Hiện chưa tìm thấy cơ sở phù hợp" + where + " có chuyên khoa " + specialtyName(specialty) + ".",
                    null,
                    List.of(),
                    null,
                    List.of(),
                    false
            );
        }

        LocalDate fromDate = datePreference == null ? LocalDate.now() : datePreference;
        DoctorSlots best = null;
        for (BranchSpecialty branchSpecialty : matches) {
            Branch branch = branchSpecialty.getBranch();
            List<DoctorProfile> doctors = doctorProfileRepository.findActiveByBranchAndSpecialty(branch.getId(), specialty.getId())
                    .stream()
                    .filter(doctor -> !publicValidSpecialties(doctor).isEmpty())
                    .toList();
            for (DoctorProfile doctor : doctors) {
                List<AiAvailableSlotResponse> slots = findNearestSlotsForDoctor(
                        doctor,
                        specialty,
                        fromDate,
                        MAX_INITIAL_SLOTS,
                        timePreference,
                        Set.of()
                );
                if (slots.isEmpty()) {
                    continue;
                }
                DoctorSlots candidate = new DoctorSlots(doctor, slots, scoreDoctorRecommendation(context, doctor, slots, null), false);
                if (isBetterDoctorCandidate(candidate, best)) {
                    best = candidate;
                }
            }
        }

        if (best == null) {
            String where = location.hasLocationSignal() ? " tại " + location.displayLabelOr("khu vực này") : "";
            return buildResponse(
                    context,
                    "AVAILABILITY_SEARCH",
                    "Tôi tìm thấy chuyên khoa " + specialtyName(specialty) + where
                            + ", nhưng hiện chưa có lịch trống phù hợp theo thời gian bạn hỏi.",
                    null,
                    List.of(),
                    null,
                    branchActions(matches.stream().map(BranchSpecialty::getBranch).toList(), specialty),
                    false
            );
        }

        String message = "Hiện có lịch " + specialtyName(specialty)
                + " tại " + facilityName(best.doctor().getBranch())
                + ". Bác sĩ phù hợp: " + best.doctor().getFullName() + ".";
        return responseForDoctorSlots(
                context,
                best.doctor(),
                specialty,
                best.slots(),
                "AVAILABILITY_SEARCH",
                message
        );
    }

    private PublicAssistantResponse handlePublicServiceContext(AiConversationContext context,
                                                              PublicServiceMatch serviceMatch,
                                                              PublicLocationMatch location) {
        String where = location.hasLocationSignal() ? " tại " + location.displayLabelOr("khu vực này") : "";
        List<MedicalService> services = serviceMatch == null ? List.of() : serviceMatch.matchedServices();
        String serviceText = serviceMatch != null && blankToNull(serviceMatch.keyword()) != null
                ? " phù hợp với \"" + serviceMatch.keyword().trim() + "\""
                : "";
        boolean listingAllServices = services.isEmpty() && isServiceListQuestion(serviceMatch == null ? "" : serviceMatch.keyword());
        if (listingAllServices) {
            services = activePublicMedicalServices();
            serviceText = "";
        }
        if (services.isEmpty()) {
            return buildResponse(
                    context,
                    "SERVICE_SEARCH",
                    "Hiện chưa tìm thấy dịch vụ y tế công khai" + serviceText + where + ".",
                    null,
                    List.of(),
                    null,
                    List.of(action("Xem dịch vụ y tế", "NAVIGATE", "/medical-services", Map.of())),
                    false
            );
        }

        if (location.hasLocationSignal()) {
            updateContextForBranchResults(context, location.matchedBranches());
        }
        StringBuilder message = new StringBuilder("PrimeCare có dịch vụ công khai phù hợp");
        message.append(serviceText).append(":\n");
        for (MedicalService service : services.stream().limit(5).toList()) {
            message.append("- ").append(medicalServiceName(service));
            if (blankToNull(service.getDescriptionVn()) != null) {
                message.append(" - ").append(truncateServiceDescription(service.getDescriptionVn()));
            }
            message.append('\n');
        }
        message.append("Dữ liệu công khai hiện là danh mục dịch vụ chung, chưa xác nhận phân bổ dịch vụ theo từng cơ sở");
        message.append(where).append(".");
        message.append(" Bạn muốn xem chuyên khoa hoặc bác sĩ phù hợp để đặt lịch không?");

        return buildResponse(
                context,
                "SERVICE_SEARCH",
                message.toString().trim(),
                null,
                List.of(),
                null,
                List.of(action("Xem dịch vụ y tế", "NAVIGATE", "/medical-services", Map.of())),
                false
        );
    }

    private PublicAssistantResponse handlePublicDoctorContext(AiConversationContext context,
                                                              String question,
                                                              String normalized,
                                                              List<DoctorProfile> doctorMatches,
                                                              String doctorNameQuery,
                                                              Specialty specialty,
                                                              PublicLocationMatch location,
                                                              LocalDate datePreference,
                                                              TimePreference timePreference) {
        boolean bookingIntent = hasPublicDoctorBookingIntent(normalized);
        if (isDoctorGenderQuestion(normalized)) {
            return buildResponse(
                    context,
                    "DOCTOR_SEARCH",
                    "Hiện hệ thống công khai chưa hỗ trợ lọc bác sĩ theo giới tính. Bạn có thể tìm theo tên, chuyên khoa hoặc cơ sở.",
                    null,
                    List.of(),
                    null,
                    List.of(action("Xem danh sách bác sĩ", "VIEW_DOCTORS", "/doctors", Map.of())),
                    false
            );
        }

        List<DoctorProfile> candidates = doctorMatches;
        if (candidates.isEmpty() && specialty != null) {
            candidates = findPublicDoctorsBySpecialtyAndLocation(specialty, location);
        }
        if (candidates.isEmpty() && specialty == null && location.hasLocationSignal()) {
            candidates = findPublicDoctorsByLocation(location);
        }
        if (candidates.isEmpty()
                && specialty == null
                && !location.hasLocationSignal()
                && blankToNull(doctorNameQuery) == null
                && isGeneralDoctorContextQuestion(normalized)) {
            return handleGeneralDoctorContext(context, normalized);
        }

        if (!doctorMatches.isEmpty() && specialty != null) {
            candidates = candidates.stream()
                    .filter(doctor -> publicValidSpecialties(doctor).stream()
                            .anyMatch(item -> specialty.getId().equals(item.getId())))
                    .toList();
        }
        if (!doctorMatches.isEmpty() && location.hasLocationSignal()) {
            candidates = candidates.stream()
                    .filter(doctor -> doctor.getBranch() != null && location.matches(doctor.getBranch()))
                    .toList();
        }

        if (candidates.isEmpty()) {
            if (blankToNull(doctorNameQuery) != null) {
                return buildResponse(
                        context,
                        "DOCTOR_SEARCH",
                        "Hiện chưa tìm thấy bác sĩ khả dụng có tên '" + displayDoctorNameQuery(doctorNameQuery) + "' trên hệ thống PrimeCare.",
                        null,
                        List.of(),
                        null,
                        List.of(),
                        false
                );
            }
            String where = location.hasLocationSignal() ? " tại " + location.displayLabelOr("khu vực này") : "";
            String specialtyText = specialty == null ? "" : " " + specialtyName(specialty);
            return buildResponse(
                    context,
                    "DOCTOR_SEARCH",
                    "Hiện chưa tìm thấy bác sĩ" + specialtyText + where + " phù hợp trong dữ liệu công khai.",
                    null,
                    List.of(),
                    null,
                    List.of(),
                    false
            );
        }

        if (doctorMatches.size() > 1 && candidates.size() > 1 && !hasExactDoctorNameMatch(normalized, candidates)) {
            return doctorCandidateClarification(context, candidates, specialty, doctorNameQuery, bookingIntent);
        }

        if (candidates.size() > 1
                && !bookingIntent
                && !isPublicDoctorAvailabilityQuestion(normalized)
                && !hasExactDoctorNameMatch(normalized, candidates)) {
            return doctorCandidateClarification(context, candidates, specialty, doctorNameQuery, false);
        }

        DoctorProfile doctor = candidates.get(0);
        Specialty effectiveSpecialty = specialty != null
                ? specialty
                : resolveDoctorSpecialty(doctor, context.getCurrentSpecialtyId()).orElse(null);
        if (effectiveSpecialty == null) {
            return doctorSpecialtyClarification(context, doctor);
        }

        if (containsAny(normalized, "chuyen khoa gi", "chuyen gi", "khoa gi", "specialty")) {
            List<String> specialtyNames = publicValidSpecialties(doctor).stream()
                    .map(this::specialtyName)
                    .distinct()
                    .toList();
            String message = specialtyNames.isEmpty()
                    ? "Tôi chưa có dữ liệu chuyên khoa công khai của " + doctor.getFullName() + "."
                    : doctor.getFullName() + " đang khám " + String.join(", ", specialtyNames) + ".";
            return buildResponse(
                    context,
                    "DOCTOR_RECOMMENDATION",
                    message,
                    suggestedDoctor(doctor, effectiveSpecialty),
                    List.of(),
                    null,
                    doctorActions(List.of(doctor), effectiveSpecialty, bookingIntent),
                    false
            );
        }

        if (containsAny(normalized, "o dau", "co so nao", "chi nhanh nao", "dia chi", "phong kham nao")) {
            Branch branch = doctor.getBranch();
            String message = doctor.getFullName() + " đang khám tại " + facilityName(branch);
            if (blankToNull(facilityAddress(branch)) != null) {
                message += " - " + facilityAddress(branch);
            }
            message += ".";
            return buildResponse(
                    context,
                    "DOCTOR_RECOMMENDATION",
                    message,
                    suggestedDoctor(doctor, effectiveSpecialty),
                    List.of(),
                    null,
                    doctorActions(List.of(doctor), effectiveSpecialty, bookingIntent),
                    false
            );
        }

        Optional<LocalTime> exactTime = parseClockTime(question, normalized);
        if (datePreference != null
                && exactTime.isPresent()
                && (bookingIntent || containsAny(normalized, "con lich", "lich kham", "ca kham"))) {
            PublicAssistantResponse draftResponse = tryCreateExactSlotDraft(
                    context,
                    doctor,
                    effectiveSpecialty,
                    datePreference,
                    exactTime.get(),
                    "BOOKING_DRAFT_CREATED"
            ).orElse(null);
            if (draftResponse != null) {
                return draftResponse;
            }
        }

        if (bookingIntent
                && datePreference == null
                && timePreference == null
                && !containsAny(normalized, "con lich", "lich trong", "lich kham ngay", "lich kham mai")) {
            AiSuggestedDoctorResponse suggestedDoctor = suggestedDoctor(doctor, effectiveSpecialty);
            updateContextForDoctor(context, suggestedDoctor, List.of(), "CLARIFICATION");
            return buildResponse(
                    context,
                    "CLARIFICATION",
                    "Bạn muốn khám ngày nào hoặc buổi nào để tôi kiểm tra lịch trống cho " + doctor.getFullName() + "?",
                    suggestedDoctor,
                    List.of(),
                    null,
                    doctorActions(List.of(doctor), effectiveSpecialty),
                    true
            );
        }

        if (datePreference != null || timePreference != null || bookingIntent || isPublicDoctorAvailabilityQuestion(normalized)) {
            return showSlotsForDoctor(
                    context,
                    doctor,
                    effectiveSpecialty,
                    datePreference,
                    timePreference,
                    "SHOW_AVAILABLE_SLOTS",
                    Set.of()
            );
        }

        return doctorCandidateAnswer(context, List.of(doctor), effectiveSpecialty, doctorNameQuery, bookingIntent);
    }

    private PublicAssistantResponse doctorCandidateClarification(AiConversationContext context,
                                                                 List<DoctorProfile> doctors,
                                                                 Specialty specialty,
                                                                 String doctorNameQuery,
                                                                 boolean includeBookingAction) {
        StringBuilder message = new StringBuilder();
        if (blankToNull(doctorNameQuery) != null) {
            message.append("PrimeCare hiện có bác sĩ phù hợp với tên '")
                    .append(displayDoctorNameQuery(doctorNameQuery))
                    .append("':\n");
        } else {
            message.append("Tôi tìm thấy một vài bác sĩ phù hợp:\n");
        }
        for (DoctorProfile doctor : doctors.stream().limit(4).toList()) {
            Specialty displaySpecialty = specialty != null
                    ? specialty
                    : resolveDoctorSpecialty(doctor, null).orElse(null);
            message.append("- ").append(doctor.getFullName());
            if (displaySpecialty != null) {
                message.append(" - ").append(specialtyName(displaySpecialty));
            }
            if (doctor.getBranch() != null) {
                message.append(" tại ").append(facilityName(doctor.getBranch()));
            }
            message.append('\n');
        }
        message.append("Bạn muốn chọn bác sĩ nào?");

        updateContextForDoctorResults(context, doctors, specialty, "DOCTOR_SEARCH");
        return buildResponse(
                context,
                "DOCTOR_SEARCH",
                message.toString().trim(),
                null,
                List.of(),
                null,
                doctorActions(doctors, specialty, includeBookingAction),
                true
        );
    }

    private PublicAssistantResponse doctorSpecialtyClarification(AiConversationContext context, DoctorProfile doctor) {
        return buildResponse(
                context,
                "DOCTOR_SEARCH",
                "Tôi đã tìm thấy bác sĩ, nhưng chưa xác định được chuyên khoa công khai để kiểm tra lịch. Bạn muốn khám chuyên khoa nào?",
                null,
                List.of(),
                null,
                doctorActions(List.of(doctor), null),
                true
        );
    }

    private PublicAssistantResponse doctorCandidateAnswer(AiConversationContext context,
                                                         List<DoctorProfile> doctors,
                                                         Specialty specialty,
                                                         String doctorNameQuery,
                                                         boolean includeBookingAction) {
        StringBuilder message = new StringBuilder();
        if (blankToNull(doctorNameQuery) != null) {
            message.append("PrimeCare hiện có bác sĩ phù hợp với tên '")
                    .append(displayDoctorNameQuery(doctorNameQuery))
                    .append("':\n");
        } else {
            message.append("Tôi tìm thấy bác sĩ phù hợp:\n");
        }
        for (DoctorProfile doctor : doctors.stream().limit(4).toList()) {
            Specialty displaySpecialty = specialty != null
                    ? specialty
                    : resolveDoctorSpecialty(doctor, null).orElse(null);
            message.append("- ").append(doctor.getFullName());
            if (displaySpecialty != null) {
                message.append(" - ").append(specialtyName(displaySpecialty));
            }
            if (doctor.getBranch() != null) {
                message.append(" tại ").append(facilityName(doctor.getBranch()));
            }
            message.append('\n');
        }
        message.append("Bạn có thể chọn bác sĩ hoặc để tôi gợi ý lịch gần nhất.");

        Specialty singleSpecialty = doctors.size() == 1
                ? (specialty != null ? specialty : resolveDoctorSpecialty(doctors.get(0), null).orElse(null))
                : null;
        AiSuggestedDoctorResponse suggestedDoctor = doctors.size() == 1 && singleSpecialty != null
                ? suggestedDoctor(doctors.get(0), singleSpecialty)
                : null;
        updateContextForDoctorResults(context, doctors, specialty, "DOCTOR_SEARCH");
        return buildResponse(
                context,
                "DOCTOR_SEARCH",
                message.toString().trim(),
                suggestedDoctor,
                List.of(),
                null,
                doctorActions(doctors, specialty, includeBookingAction),
                false
        );
    }

    private Optional<PublicAssistantResponse> tryCreateExactSlotDraft(AiConversationContext context,
                                                                     DoctorProfile doctor,
                                                                     Specialty specialty,
                                                                     LocalDate date,
                                                                     LocalTime exactStart,
                                                                     String intent) {
        List<AiAvailableSlotResponse> slots = findNearestSlotsForDoctor(
                doctor,
                specialty,
                date,
                20,
                null,
                Set.of()
        ).stream()
                .filter(slot -> date.equals(slot.getAppointmentDate()))
                .filter(slot -> exactStart.equals(parseSlotTime(slot.getStartTime())))
                .toList();

        if (slots.size() != 1) {
            return Optional.empty();
        }

        SlotValidation validation = validateSlot(keyFromSlot(slots.get(0)));
        if (!validation.available() || validation.slot() == null) {
            return Optional.empty();
        }

        AiBookingDraftResponse bookingDraft = bookingDraft(validation.slot());
        if (!isCompleteBookingDraft(bookingDraft)) {
            return Optional.empty();
        }

        AiAvailableSlotResponse slot = validation.slot();
        context.setPendingBookingDraft(bookingDraft);
        context.setCurrentDoctorId(slot.getDoctorId());
        context.setCurrentDoctorName(slot.getDoctorName());
        context.setCurrentSpecialtyId(slot.getSpecialtyId());
        context.setCurrentSpecialtyName(slot.getSpecialtyName());
        context.setCurrentFacilityId(slot.getFacilityId());
        context.setCurrentFacilityName(slot.getFacilityName());
        context.setCurrentFacilityAddress(slot.getFacilityAddress());

        return Optional.of(buildResponse(
                context,
                intent,
                "Tôi đã tìm thấy đúng ca bạn chọn và tạo bản nháp đặt lịch. Bạn kiểm tra lại thông tin trước khi xác nhận.",
                null,
                List.of(),
                bookingDraft,
                List.of(goToBookingAction(bookingDraft)),
                false
        ));
    }

    private List<BranchSpecialty> activeBranchSpecialtiesFor(Specialty specialty, PublicLocationMatch location) {
        if (specialty == null || specialty.getId() == null) {
            return List.of();
        }
        return branchSpecialtyRepository.findActiveBySpecialtyId(specialty.getId())
                .stream()
                .filter(item -> item.getBranch() != null && item.getBranch().getStatus() == BranchStatus.ACTIVE)
                .filter(item -> location == null || !location.hasLocationSignal() || location.matches(item.getBranch()))
                .toList();
    }

    private List<String> publicSpecialtyNamesForBranch(Long branchId, int limit) {
        if (branchId == null) {
            return List.of();
        }
        return branchSpecialtyRepository.findActiveByBranchId(branchId)
                .stream()
                .map(BranchSpecialty::getSpecialty)
                .filter(Objects::nonNull)
                .map(this::specialtyName)
                .filter(Objects::nonNull)
                .distinct()
                .limit(limit)
                .toList();
    }

    private PublicServiceMatch resolvePublicServiceMatch(String normalized) {
        if (!isServiceContextQuestion(normalized)) {
            return new PublicServiceMatch(null, List.of());
        }
        String keyword = extractServiceKeyword(normalized);
        List<ScoredMedicalService> scored = new ArrayList<>();
        for (MedicalService service : activePublicMedicalServices()) {
            int score = scoreMedicalService(service, normalized, keyword);
            if (score >= 25) {
                scored.add(new ScoredMedicalService(service, score));
            }
        }
        List<MedicalService> matches = scored.stream()
                .sorted(Comparator.comparingInt(ScoredMedicalService::score).reversed()
                        .thenComparing(item -> medicalServiceName(item.service())))
                .map(ScoredMedicalService::service)
                .limit(5)
                .toList();
        return new PublicServiceMatch(keyword, matches);
    }

    private List<MedicalService> activePublicMedicalServices() {
        List<MedicalService> services = medicalServiceRepository
                .findByPublicVisibleTrueAndStatusOrderByDisplayOrderAscNameVnAsc(MedicalServiceStatus.ACTIVE);
        if (services == null) {
            return List.of();
        }
        return services.stream()
                .filter(service -> service != null
                        && service.getStatus() == MedicalServiceStatus.ACTIVE
                        && Boolean.TRUE.equals(service.getPublicVisible()))
                .toList();
    }

    private int scoreMedicalService(MedicalService service, String normalized, String keyword) {
        String corpus = searchable(String.join(" ",
                Objects.toString(service.getCode(), ""),
                Objects.toString(service.getNameVn(), ""),
                Objects.toString(service.getNameEn(), ""),
                Objects.toString(service.getDescriptionVn(), ""),
                Objects.toString(service.getDescriptionEn(), ""),
                Objects.toString(service.getServiceType(), ""),
                Objects.toString(service.getDepartmentCode(), "")
        ));
        String query = searchable(keyword);
        if (corpus.isBlank() || query.isBlank()) {
            return 0;
        }

        int score = 0;
        if (corpus.contains(query) || normalized.contains(corpus)) {
            score += 180 + query.length();
        }
        score += scoreMedicalServiceAliases(normalized, corpus);

        for (String token : query.split(" ")) {
            if (isWeakServiceToken(token)) {
                continue;
            }
            if (containsPhrase(corpus, token)) {
                score += Math.max(25, token.length() * 6);
            }
        }
        return score;
    }

    private int scoreMedicalServiceAliases(String normalized, String corpus) {
        int score = 0;
        if (containsAnyPhrase(normalized, List.of("xet nghiem mau", "cong thuc mau", "blood test", "complete blood count", "cbc"))
                && containsAnyPhrase(corpus, List.of("cong thuc mau", "huyet hoc", "cbc", "complete blood count", "mau"))) {
            score += 160;
        }
        if (containsAnyPhrase(normalized, List.of("duong huyet", "glucose", "xet nghiem duong"))
                && containsAnyPhrase(corpus, List.of("duong huyet", "glucose", "blood glucose"))) {
            score += 160;
        }
        if (containsAnyPhrase(normalized, List.of("sieu am", "ultrasound", "echo"))
                && containsAnyPhrase(corpus, List.of("sieu am", "ultrasound", "echo", "doppler"))) {
            score += 150;
        }
        if (containsAnyPhrase(normalized, List.of("x quang", "xray", "x ray", "chup"))
                && containsAnyPhrase(corpus, List.of("x quang", "xray", "x ray", "chup"))) {
            score += 150;
        }
        if (containsAnyPhrase(normalized, List.of("dien tam do", "dien tim", "ecg", "ekg"))
                && containsAnyPhrase(corpus, List.of("dien tam do", "electrocardiogram", "ecg", "ekg"))) {
            score += 150;
        }
        if (containsAnyPhrase(normalized, List.of("noi soi", "gastroscopy", "endoscopy"))
                && containsAnyPhrase(corpus, List.of("noi soi", "gastroscopy", "endoscopy"))) {
            score += 150;
        }
        return score;
    }

    private boolean isWeakServiceToken(String token) {
        return token == null
                || token.isBlank()
                || Set.of("dich", "vu", "xet", "nghiem", "kham", "lam", "co", "khong", "nao", "chi", "nhanh",
                "so", "phong", "primecare", "tai", "o", "gan", "chup", "service")
                .contains(token);
    }

    private String extractServiceKeyword(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        String cleaned = normalized;
        int branchStop = firstStopIndex(cleaned, List.of(
                " o ",
                " tai ",
                " gan ",
                " chi nhanh ",
                " co so ",
                " phong kham "
        ));
        if (branchStop > 0) {
            cleaned = cleaned.substring(0, branchStop).trim();
        }
        cleaned = cleaned.replaceAll("\\b(?:primecare|co|khong|dich vu|lam|thuc hien|nao|tai|o|gan|chi nhanh|co so|phong kham)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return blankToNull(cleaned);
    }

    private String medicalServiceName(MedicalService service) {
        return firstNonBlank(service.getNameVn(), service.getNameEn(), service.getCode());
    }

    private String truncateServiceDescription(String value) {
        String stripped = PublicAssistantText.stripHtml(value);
        if (stripped == null) {
            return null;
        }
        String compact = stripped.replaceAll("\\s+", " ").trim();
        return compact.length() <= 90 ? compact : compact.substring(0, 87).trim() + "...";
    }

    private List<Specialty> publicValidSpecialties(DoctorProfile doctor) {
        if (doctor == null || doctor.getStatus() != DoctorStatus.ACTIVE
                || doctor.getBranch() == null || doctor.getBranch().getId() == null
                || doctor.getBranch().getStatus() != BranchStatus.ACTIVE
                || doctor.getDoctorSpecialties() == null) {
            return List.of();
        }
        return doctor.getDoctorSpecialties().stream()
                .map(DoctorSpecialty::getSpecialty)
                .filter(Objects::nonNull)
                .filter(specialty -> specialty.getId() != null && "ACTIVE".equalsIgnoreCase(Objects.toString(specialty.getStatus(), "")))
                .filter(specialty -> branchSpecialtyRepository.existsByBranch_IdAndSpecialty_IdAndStatus(
                        doctor.getBranch().getId(),
                        specialty.getId(),
                        BranchSpecialtyStatus.ACTIVE
                ))
                .distinct()
                .toList();
    }

    private List<DoctorProfile> findPublicDoctorsBySpecialtyAndLocation(Specialty specialty, PublicLocationMatch location) {
        if (specialty == null || specialty.getId() == null) {
            return List.of();
        }
        List<BranchSpecialty> branchSpecialties = activeBranchSpecialtiesFor(specialty, location);
        List<DoctorProfile> doctors = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (BranchSpecialty branchSpecialty : branchSpecialties) {
            Branch branch = branchSpecialty.getBranch();
            if (branch == null || branch.getId() == null) {
                continue;
            }
            for (DoctorProfile doctor : doctorProfileRepository.findActiveByBranchAndSpecialty(branch.getId(), specialty.getId())) {
                if (doctor.getId() != null && seen.add(doctor.getId()) && !publicValidSpecialties(doctor).isEmpty()) {
                    doctors.add(doctor);
                }
            }
        }
        return doctors.stream()
                .sorted(Comparator.comparing(DoctorProfile::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(10)
                .toList();
    }

    private PublicAssistantResponse handleGeneralDoctorContext(AiConversationContext context, String normalized) {
        List<DoctorProfile> doctors = publicDoctorCandidates();
        if (doctors.isEmpty()) {
            return buildResponse(
                    context,
                    "DOCTOR_SEARCH",
                    "Hiện chưa tìm thấy bác sĩ khả dụng trong dữ liệu công khai của PrimeCare.",
                    null,
                    List.of(),
                    null,
                    List.of(),
                    false
            );
        }

        if (containsAny(normalized, "chuyen khoa gi", "chuyen khoa nao", "khoa gi")) {
            List<String> specialtyNames = doctors.stream()
                    .flatMap(doctor -> publicValidSpecialties(doctor).stream())
                    .map(this::specialtyName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(8)
                    .toList();
            String message = specialtyNames.isEmpty()
                    ? "Hiện chưa có dữ liệu chuyên khoa công khai cho các bác sĩ khả dụng."
                    : "PrimeCare hiện có bác sĩ ở các chuyên khoa: " + String.join(", ", specialtyNames) + ".";
            return buildResponse(
                    context,
                    "DOCTOR_SEARCH",
                    message,
                    null,
                    List.of(),
                    null,
                    List.of(action("Xem danh sách bác sĩ", "VIEW_DOCTORS", "/doctors", Map.of())),
                    false
            );
        }

        if (containsAny(normalized, "co so nao", "chi nhanh nao", "lam o dau")) {
            List<String> branchNames = doctors.stream()
                    .map(DoctorProfile::getBranch)
                    .filter(Objects::nonNull)
                    .map(this::facilityName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(8)
                    .toList();
            String message = branchNames.isEmpty()
                    ? "Hiện chưa có dữ liệu cơ sở công khai cho các bác sĩ khả dụng."
                    : "Các bác sĩ khả dụng hiện làm tại: " + String.join(", ", branchNames) + ".";
            return buildResponse(
                    context,
                    "DOCTOR_SEARCH",
                    message,
                    null,
                    List.of(),
                    null,
                    List.of(action("Xem danh sách bác sĩ", "VIEW_DOCTORS", "/doctors", Map.of())),
                    false
            );
        }

        StringBuilder message = new StringBuilder();
        if (containsAny(normalized, "bao nhieu")) {
            message.append("PrimeCare hiện có ").append(doctors.size()).append(" bác sĩ khả dụng trong dữ liệu công khai");
            if (!doctors.isEmpty()) {
                message.append(", gồm một số bác sĩ như:\n");
            } else {
                message.append(".");
            }
        } else {
            message.append("PrimeCare hiện có các bác sĩ khả dụng:\n");
        }
        for (DoctorProfile doctor : doctors.stream().limit(5).toList()) {
            Specialty specialty = resolveDoctorSpecialty(doctor, null).orElse(null);
            message.append("- ").append(doctor.getFullName());
            if (specialty != null) {
                message.append(" - ").append(specialtyName(specialty));
            }
            if (doctor.getBranch() != null) {
                message.append(" tại ").append(facilityName(doctor.getBranch()));
            }
            message.append('\n');
        }
        message.append("Bạn muốn xem theo chuyên khoa, cơ sở hay tên bác sĩ cụ thể?");

        return buildResponse(
                context,
                "DOCTOR_SEARCH",
                message.toString().trim(),
                null,
                List.of(),
                null,
                doctorActions(doctors, null, false),
                false
        );
    }

    private List<DoctorProfile> findPublicDoctorsByLocation(PublicLocationMatch location) {
        if (location == null || !location.hasLocationSignal()) {
            return List.of();
        }
        return publicDoctorCandidates().stream()
                .filter(doctor -> doctor.getBranch() != null && location.matches(doctor.getBranch()))
                .sorted(Comparator.comparing(DoctorProfile::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(10)
                .toList();
    }

    private List<DoctorProfile> publicDoctorCandidates() {
        var doctorPage = doctorProfileRepository.search(
                null,
                null,
                null,
                DoctorStatus.ACTIVE,
                PageRequest.of(0, 150, Sort.by("createdAt").descending())
        );
        List<DoctorProfile> doctors = doctorPage == null ? List.of() : doctorPage.getContent();
        return doctors.stream()
                .filter(doctor -> doctor != null && !publicValidSpecialties(doctor).isEmpty())
                .toList();
    }

    private List<DoctorProfile> findPublicDoctorMatchesByName(String normalized, String nameQuery) {
        if (blankToNull(nameQuery) == null) {
            return List.of();
        }

        List<DoctorProfile> doctors = publicDoctorCandidates();

        List<ScoredDoctor> scored = new ArrayList<>();
        for (DoctorProfile doctor : doctors) {
            int score = scoreDoctorNameMatch(doctor, normalized, nameQuery);
            if (score >= 10) {
                scored.add(new ScoredDoctor(doctor, score));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(ScoredDoctor::score).reversed()
                        .thenComparing(item -> Objects.toString(item.doctor().getFullName(), "")))
                .map(ScoredDoctor::doctor)
                .limit(6)
                .toList();
    }

    private int scoreDoctorNameMatch(DoctorProfile doctor, String normalized, String nameQuery) {
        String doctorName = searchable(doctor.getFullName());
        String query = searchable(nameQuery);
        if (doctorName.isBlank() || query.isBlank()) {
            return 0;
        }
        int score = 0;
        if (containsPhrase(normalized, doctorName) || containsPhrase(doctorName, query)) {
            score += 120 + query.length();
        }
        for (String token : query.split(" ")) {
            if (isWeakDoctorNameToken(token)) {
                continue;
            }
            if (containsPhrase(doctorName, token)) {
                score += Math.max(8, token.length() * 4);
            }
        }
        if (query.length() == 1 && containsPhrase(doctorName, query)) {
            score += 10;
        }
        return score;
    }

    private boolean isWeakDoctorNameToken(String token) {
        return token == null
                || token.isBlank()
                || Set.of("bac", "si", "bs", "doctor", "ten", "kham", "lich", "co", "o", "tai", "dau", "nao", "khong",
                "sang", "chieu", "toi", "mai", "hom", "nay", "ngay", "tuan", "lam", "primecare", "tim", "muon", "dat",
                "nhung", "danh", "sach", "hien", "bao", "nhieu", "chuyen", "khoa", "ho", "ai", "giup", "minh", "ben", "la")
                .contains(token);
    }

    private boolean hasExactDoctorNameMatch(String normalized, List<DoctorProfile> doctors) {
        return doctors != null && doctors.stream()
                .map(DoctorProfile::getFullName)
                .map(PublicAssistantText::searchable)
                .anyMatch(name -> !name.isBlank() && containsPhrase(normalized, name));
    }

    private String resolveDoctorNameQuery(String question, String normalized) {
        String nameQuery = extractDoctorNameQuery(question, normalized);
        if (blankToNull(nameQuery) == null) {
            nameQuery = extractFreeformDoctorNameQuery(normalized);
        }
        return blankToNull(nameQuery);
    }

    private boolean isLikelySpecialtyNameQuery(String doctorNameQuery, Specialty specialty) {
        String query = searchable(doctorNameQuery);
        if (query.isBlank() || specialty == null) {
            return false;
        }
        String specialtyCorpus = searchable(String.join(" ",
                Objects.toString(specialty.getCode(), ""),
                Objects.toString(specialty.getNameVn(), ""),
                Objects.toString(specialty.getNameEn(), "")
        ));
        return containsPhrase(specialtyCorpus, query)
                || containsPhrase(query, specialtyCorpus)
                || detectDirectSpecialtyGroup(query).isPresent();
    }

    private Set<String> tokenizeNameQuery(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : query.split(" ")) {
            if (!isWeakDoctorNameToken(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String extractDoctorNameQuery(String question, String normalized) {
        String safe = normalized == null || normalized.isBlank() ? searchable(question) : normalized;
        if (!isDoctorQuestion(safe)) {
            return null;
        }

        Matcher namedDoctor = Pattern.compile("\\b(?:co\\s+)?(?:ben minh\\s+)?(?:primecare\\s+)?(?:ai\\s+)?(?:bac si\\s+)?(?:nao\\s+)?(?:ten|ho)\\s+(.+?)(?:\\s+la\\s+(?:bac si|bs|doctor)|\\s+khong|$)").matcher(safe);
        if (namedDoctor.find()) {
            return cleanupDoctorNameSegment(namedDoctor.group(1));
        }
        Matcher nameAfterTen = Pattern.compile("\\b(?:bac si ten|bs ten|ten bac si|ten bs|doctor named)\\s+(.+)$").matcher(safe);
        if (nameAfterTen.find()) {
            return cleanupDoctorNameSegment(nameAfterTen.group(1));
        }
        Matcher nameAfterDoctor = Pattern.compile("\\b(?:bac si|bs|doctor)\\s+(.+)$").matcher(safe);
        if (nameAfterDoctor.find()) {
            return cleanupDoctorNameSegment(nameAfterDoctor.group(1));
        }
        return null;
    }

    private String extractFreeformDoctorNameQuery(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (!looksLikeFreeformDoctorNameQuery(normalized)) {
            return null;
        }
        return cleanupDoctorNameSegment(normalized);
    }

    private boolean looksLikeFreeformDoctorNameQuery(String normalized) {
        boolean doctorNameContext = containsAny(normalized,
                "kham o dau",
                "thuoc chi nhanh",
                "co lam o",
                "tim bac si",
                "muon tim",
                "dat lich")
                || (isPublicDoctorAvailabilityQuestion(normalized) && !hasDirectSpecialtySignal(normalized) && !isServiceContextQuestion(normalized));
        Set<String> tokens = tokenizeNameQuery(normalized);
        if (doctorNameContext && !tokens.isEmpty() && tokens.size() <= 6) {
            return true;
        }
        return false;
    }

    private String cleanupDoctorNameSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return null;
        }
        String cleaned = segment.trim();
        int stop = firstStopIndex(cleaned, List.of(
                " co ",
                " lam ",
                " kham ",
                " co lich",
                " lich ",
                " o ",
                " tai ",
                " chuyen khoa",
                " khoa ",
                " con ",
                " dat lich",
                " sang ",
                " chieu ",
                " toi ",
                " hom nay",
                " ngay mai",
                " mai",
                " tuan ",
                " muon ",
                " la bac si",
                " la bs",
                " la doctor",
                " khong",
                " nao",
                " gi"
        ));
        if (stop >= 0) {
            cleaned = cleaned.substring(0, stop).trim();
        }
        cleaned = cleaned.replaceAll("\\b(?:bac si|bs|doctor|ten|ho|co|khong|nao|tim|giup toi|ben minh|primecare|ai|la|nhung|danh sach|hien co)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (tokenizeNameQuery(cleaned).isEmpty()) {
            return null;
        }
        return blankToNull(cleaned);
    }

    private String displayDoctorNameQuery(String nameQuery) {
        String normalized = searchable(nameQuery);
        if (normalized.isBlank()) {
            return "bác sĩ";
        }
        StringBuilder builder = new StringBuilder();
        for (String token : normalized.split(" ")) {
            if (token.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(token.substring(0, 1).toUpperCase(Locale.ROOT));
            if (token.length() > 1) {
                builder.append(token.substring(1));
            }
        }
        return builder.isEmpty() ? "bác sĩ" : builder.toString();
    }

    private PublicLocationMatch resolveLocationMatch(String normalized, List<Branch> activeBranches) {
        List<Branch> directMatches = activeBranches.stream()
                .filter(branch -> branchMentioned(normalized, branch))
                .toList();
        String keyword = extractLocationKeyword(normalized);
        if (blankToNull(keyword) == null && directMatches.size() == 1) {
            keyword = facilityName(directMatches.get(0));
        }

        String normalizedKeyword = canonicalLocationKeyword(searchable(keyword));
        List<Branch> matched = normalizedKeyword.isBlank()
                ? directMatches
                : activeBranches.stream()
                .filter(branch -> branchMatchesLocationKeyword(branch, normalizedKeyword))
                .toList();
        if (matched.isEmpty() && !directMatches.isEmpty()) {
            matched = directMatches;
        }

        String displayLabel = displayLocationLabel(keyword, matched);
        return new PublicLocationMatch(keyword, displayLabel, matched);
    }

    private boolean branchMentioned(String normalized, Branch branch) {
        if (branch == null || normalized == null || normalized.isBlank()) {
            return false;
        }
        String code = searchable(branch.getCode());
        String nameVn = searchable(branch.getNameVn());
        String nameEn = searchable(branch.getNameEn());
        return (!code.isBlank() && containsPhrase(normalized, code))
                || (!nameVn.isBlank() && containsPhrase(normalized, nameVn))
                || (!nameEn.isBlank() && containsPhrase(normalized, nameEn));
    }

    private boolean branchMatchesLocationKeyword(Branch branch, String normalizedKeyword) {
        if (branch == null || normalizedKeyword == null || normalizedKeyword.isBlank()) {
            return false;
        }
        normalizedKeyword = canonicalLocationKeyword(normalizedKeyword);
        String corpus = searchable(String.join(" ",
                Objects.toString(branch.getCode(), ""),
                Objects.toString(branch.getNameVn(), ""),
                Objects.toString(branch.getNameEn(), ""),
                Objects.toString(branch.getAddressVn(), ""),
                Objects.toString(branch.getAddressEn(), "")
        ));
        for (String alias : locationKeywordAliases(normalizedKeyword)) {
            if (!alias.isBlank() && containsPhrase(corpus, alias)) {
                return true;
            }
        }
        if (corpus.contains(normalizedKeyword) || normalizedKeyword.contains(corpus)) {
            return true;
        }
        for (String token : normalizedKeyword.split(" ")) {
            if (token.length() >= 2 && containsPhrase(corpus, token)) {
                return true;
            }
        }
        return false;
    }

    private static String canonicalLocationKeyword(String normalizedKeyword) {
        return switch (searchable(normalizedKeyword)) {
            case "hn", "hanoi" -> "ha noi";
            case "hcm", "tphcm", "tp hcm", "ho chi minh", "sai gon" -> "tp hcm";
            case "q1", "district 1" -> "quan 1";
            case "q7" -> "quan 7";
            default -> searchable(normalizedKeyword);
        };
    }

    private static List<String> locationKeywordAliases(String normalizedKeyword) {
        return switch (canonicalLocationKeyword(normalizedKeyword)) {
            case "ha noi" -> List.of("ha noi", "hanoi", "hn");
            case "tp hcm" -> List.of("tp hcm", "tphcm", "hcm", "ho chi minh", "sai gon");
            case "quan 1" -> List.of("quan 1", "q1", "district 1");
            case "quan 7" -> List.of("quan 7", "q7");
            default -> List.of(canonicalLocationKeyword(normalizedKeyword));
        };
    }

    private String extractLocationKeyword(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        for (String known : List.of(
                "ha noi",
                "hanoi",
                "hn",
                "cau giay",
                "ha dong",
                "long bien",
                "da nang",
                "ho chi minh",
                "tp hcm",
                "hcm",
                "tphcm",
                "sai gon",
                "go vap",
                "binh thanh",
                "quan 1",
                "q1",
                "district 1",
                "quan 7",
                "q7",
                "thu duc")) {
            if (containsPhrase(normalized, known)) {
                return known;
            }
        }
        Matcher matcher = Pattern.compile("\\b(?:o|tai|gan|khu vuc)\\s+(.+)$").matcher(normalized);
        if (matcher.find()) {
            return cleanupLocationSegment(matcher.group(1));
        }
        return null;
    }

    private String cleanupLocationSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return null;
        }
        String cleaned = segment.trim();
        int stop = firstStopIndex(cleaned, List.of(
                " co ",
                " khong",
                " kham ",
                " bac si",
                " chuyen khoa",
                " lich ",
                " con ",
                " nao",
                " dat lich",
                " toi ",
                " primecare"
        ));
        if (stop >= 0) {
            cleaned = cleaned.substring(0, stop).trim();
        }
        cleaned = cleaned.replaceAll("\\b(?:co so|chi nhanh|phong kham|primecare|tai|o|gan|khu vuc)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (Set.of("dau", "day", "nay", "do").contains(cleaned)) {
            return null;
        }
        return blankToNull(cleaned);
    }

    private int firstStopIndex(String value, List<String> stopPhrases) {
        int first = -1;
        for (String stopPhrase : stopPhrases) {
            int index = value.indexOf(stopPhrase);
            if (index >= 0 && (first < 0 || index < first)) {
                first = index;
            }
        }
        return first;
    }

    private String displayLocationLabel(String keyword, List<Branch> matchedBranches) {
        String normalizedKeyword = searchable(keyword);
        if (normalizedKeyword.isBlank()) {
            return matchedBranches != null && matchedBranches.size() == 1
                    ? facilityName(matchedBranches.get(0))
                    : null;
        }
        return switch (normalizedKeyword) {
            case "ha noi", "hanoi", "hn" -> "Hà Nội";
            case "cau giay" -> "Cầu Giấy";
            case "ha dong" -> "Hà Đông";
            case "long bien" -> "Long Biên";
            case "da nang" -> "Đà Nẵng";
            case "tp hcm", "hcm", "tphcm", "ho chi minh", "sai gon" -> "TP.HCM";
            case "go vap" -> "Gò Vấp";
            case "binh thanh" -> "Bình Thạnh";
            case "thu duc" -> "Thủ Đức";
            case "quan 1", "q1", "district 1" -> "Quận 1";
            case "quan 7", "q7" -> "Quận 7";
            default -> keyword.trim();
        };
    }

    private boolean isServiceContextQuestion(String normalized) {
        return containsAny(normalized,
                "dich vu",
                "xet nghiem",
                "xet nghiem mau",
                "xet nghiem tuyen giap",
                "cong thuc mau",
                "sieu am",
                "chup",
                "x quang",
                "xray",
                "ho hap ky",
                "khi dung",
                "dien tim",
                "dien tam do",
                "noi soi",
                "kham rang");
    }

    private boolean isGeneralDoctorContextQuestion(String normalized) {
        return isDoctorQuestion(normalized) && containsAnyPhrase(normalized, List.of(
                "co nhung bac si nao",
                "co bac si nao",
                "danh sach bac si",
                "bac si hien co",
                "bao nhieu bac si",
                "co bac si chuyen khoa gi",
                "bac si chuyen khoa gi",
                "co bac si o co so nao",
                "co bac si o chi nhanh nao",
                "co nhung bac si",
                "nhung bac si nao"
        ));
    }

    private boolean isDoctorGenderQuestion(String normalized) {
        return isDoctorQuestion(normalized)
                && containsAnyPhrase(normalized, List.of(
                "bac si nu",
                "bs nu",
                "doctor nu",
                "gioi tinh nu"
        ));
    }

    private boolean isViewMoreSlots(String normalized) {
        return containsAny(normalized, "xem them ca", "them ca", "con gio", "gio khac", "ca khac", "slot khac", "con lich");
    }

    private boolean isChangeDoctor(String normalized) {
        return containsAny(normalized, "bac si khac", "doi bac si", "doctor khac", "bs khac");
    }

    private boolean isFacilityQuestion(String normalized) {
        return containsAny(normalized, "co so", "dia chi", "o dau", "phong kham", "facility", "address", "gan hon");
    }

    private boolean isFacilityInfoFollowUpQuestion(String normalized) {
        if (!isFacilityQuestion(normalized) && !containsAny(normalized, "so dien thoai")) {
            return false;
        }
        if (isCountQuestion(normalized)
                || isServiceContextQuestion(normalized)
                || containsAnyPhrase(normalized, List.of("bac si nao", "co bac si", "chuyen khoa", "khoa gi", "khoa nao"))) {
            return false;
        }
        return true;
    }

    private boolean isDoctorQuestion(String normalized) {
        return containsAny(normalized, "bac si", "bs ", "doctor", "bac si nay", "bs nay");
    }

    private boolean isAvailabilityQuestion(String normalized) {
        return containsAnyPhrase(normalized, List.of(
                "lich",
                "gio",
                "ca kham",
                "slot",
                "con lich",
                "co lich",
                "lich trong",
                "som nhat",
                "gan nhat",
                "lich gan nhat",
                "hom nay",
                "ngay mai",
                "sang mai",
                "chieu mai",
                "available"
        ));
    }

    private boolean isPublicDoctorAvailabilityQuestion(String normalized) {
        return containsAnyPhrase(normalized, List.of(
                "con lich",
                "co lich",
                "lich kham",
                "lich trong",
                "con slot",
                "co slot",
                "ca kham",
                "con ca",
                "co ca"
        ));
    }

    private boolean hasDoctorOrSpecialtyContext(AiConversationContext context) {
        return context != null && (context.getCurrentDoctorId() != null || context.getCurrentSpecialtyId() != null);
    }

    private PublicAssistantResponse safetyResponse(AiConversationContext context, PublicAssistantSafety.SafetyBlock block) {
        clearBookingResponseState(context);
        String conversationId = ensureConversationId(context);
        context.setConversationId(conversationId);
        context.setLastIntent(block.intent());
        context.setLastAiAction(block.intent());
        context.setClarificationNeeded(true);
        contextCache.put(conversationId, context);
        return PublicAssistantResponse.builder()
                .answer(block.message())
                .message(block.message())
                .conversationId(conversationId)
                .intent(block.intent())
                .context(context)
                .suggestedDoctor(null)
                .availableSlots(List.of())
                .bookingDraft(null)
                .actions(List.of())
                .clarificationNeeded(true)
                .provider(PROVIDER)
                .suggestions(List.of())
                .build();
    }

    private void clearBookingResponseState(AiConversationContext context) {
        if (context == null) {
            return;
        }
        context.setCurrentSpecialtyId(null);
        context.setCurrentSpecialtyName(null);
        context.setCurrentDoctorId(null);
        context.setCurrentDoctorName(null);
        context.setCurrentFacilityId(null);
        context.setCurrentFacilityName(null);
        context.setCurrentFacilityAddress(null);
        context.setLastSuggestedDoctors(List.of());
        context.setLastAvailableSlots(List.of());
        context.setLastShownDate(null);
        context.setPendingBookingDraft(null);
        context.setUserTimePreference(null);
        context.setUserSessionType(null);
        context.setPreferredDate(null);
        context.setPreferredFromDate(null);
        context.setPreferredWeekday(null);
        context.setPreferredAfterTime(null);
        context.setPreferredBeforeTime(null);
    }

    private PublicAssistantResponse clarification(AiConversationContext context, String intent, String message, List<AiAvailableSlotResponse> slots) {
        List<AiAvailableSlotResponse> safeSlots = slots == null ? List.of() : slots;
        AiSuggestedDoctorResponse suggestedDoctor = safeSlots.isEmpty() ? null : currentSuggestedDoctor(context);
        List<PublicAssistantActionResponse> actions = safeSlots.isEmpty() ? List.of() : slotActions(safeSlots, true);
        context.setLastIntent(intent);
        context.setLastAiAction(intent);
        context.setClarificationNeeded(true);
        return buildResponse(
                context,
                intent,
                message,
                suggestedDoctor,
                safeSlots,
                null,
                actions,
                true
        );
    }

    private PublicAssistantResponse buildResponse(AiConversationContext context,
                                                  String intent,
                                                  String message,
                                                  AiSuggestedDoctorResponse suggestedDoctor,
                                                  List<AiAvailableSlotResponse> slots,
                                                  AiBookingDraftResponse bookingDraft,
                                                  List<PublicAssistantActionResponse> actions,
                                                  boolean clarificationNeeded) {
        AiBookingDraftResponse safeBookingDraft = isCompleteBookingDraft(bookingDraft) ? bookingDraft : null;
        String conversationId = ensureConversationId(context);
        context.setConversationId(conversationId);
        context.setLastIntent(intent);
        context.setLastAiAction(intent);
        context.setClarificationNeeded(clarificationNeeded);
        if (slots != null && !slots.isEmpty()) {
            context.setLastAvailableSlots(slots);
            context.setLastShownDate(slots.get(0).getAppointmentDate());
            context.setPreferredDate(slots.get(0).getAppointmentDate());
            if (context.getPreferredFromDate() == null) {
                context.setPreferredFromDate(slots.get(0).getAppointmentDate());
            }
        }
        if (suggestedDoctor != null) {
            context.setLastSuggestedDoctors(appendSuggestedDoctor(context.getLastSuggestedDoctors(), suggestedDoctor));
        }
        if (safeBookingDraft != null) {
            context.setPendingBookingDraft(safeBookingDraft);
        }
        contextCache.put(conversationId, context);

        return PublicAssistantResponse.builder()
                .answer(message)
                .message(message)
                .conversationId(conversationId)
                .intent(intent)
                .context(context)
                .suggestedDoctor(suggestedDoctor)
                .availableSlots(slots == null ? List.of() : slots)
                .bookingDraft(safeBookingDraft)
                .actions(actions == null ? List.of() : actions)
                .clarificationNeeded(clarificationNeeded)
                .provider(PROVIDER)
                .caution("Tôi chỉ gợi ý chuyên khoa, bác sĩ và lịch trống từ dữ liệu PrimeCare; không thay thế chẩn đoán hoặc điều trị của bác sĩ.")
                .suggestions(suggestionsForResponse(intent, slots, safeBookingDraft))
                .build();
    }

    private boolean isCompleteBookingDraft(AiBookingDraftResponse bookingDraft) {
        return bookingDraft != null
                && blankToNull(bookingDraft.getSlotId()) != null
                && bookingDraft.getDoctorId() != null
                && blankToNull(bookingDraft.getDoctorName()) != null
                && bookingDraft.getSpecialtyId() != null
                && blankToNull(bookingDraft.getSpecialtyName()) != null
                && bookingDraft.getFacilityId() != null
                && bookingDraft.getBranchId() != null
                && blankToNull(bookingDraft.getFacilityName()) != null
                && bookingDraft.getAppointmentDate() != null
                && blankToNull(bookingDraft.getStartTime()) != null
                && blankToNull(bookingDraft.getEndTime()) != null;
    }

    private List<String> suggestionsForResponse(String intent,
                                                List<AiAvailableSlotResponse> slots,
                                                AiBookingDraftResponse bookingDraft) {
        if (bookingDraft != null) {
            return List.of();
        }
        if ("FACILITY_INFO".equals(intent) || (intent != null && intent.endsWith("_SAFETY_BLOCK"))) {
            return List.of();
        }
        if ("CLARIFICATION".equals(intent) && (slots == null || slots.isEmpty())) {
            return List.of();
        }
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        return List.of("Xem thêm ca", "Đổi bác sĩ", "Xem địa chỉ cơ sở");
    }

    private void updateContextForDoctor(AiConversationContext context,
                                        AiSuggestedDoctorResponse doctor,
                                        List<AiAvailableSlotResponse> slots,
                                        String intent) {
        if (doctor != null) {
            context.setCurrentDoctorId(doctor.getDoctorId());
            context.setCurrentDoctorName(doctor.getDoctorName());
            context.setCurrentSpecialtyId(doctor.getSpecialtyId());
            context.setCurrentSpecialtyName(doctor.getSpecialtyName());
            context.setCurrentFacilityId(doctor.getFacilityId());
            context.setCurrentFacilityName(doctor.getFacilityName());
            context.setCurrentFacilityAddress(doctor.getFacilityAddress());
            context.setLastSuggestedDoctors(appendSuggestedDoctor(context.getLastSuggestedDoctors(), doctor));
        }
        context.setLastAvailableSlots(slots == null ? List.of() : slots);
        if (slots != null && !slots.isEmpty()) {
            context.setLastShownDate(slots.get(0).getAppointmentDate());
            context.setPreferredDate(slots.get(0).getAppointmentDate());
            if (context.getPreferredFromDate() == null) {
                context.setPreferredFromDate(slots.get(0).getAppointmentDate());
            }
        }
        context.setLastIntent(intent);
        context.setLastAiAction(intent);
        context.setClarificationNeeded(false);
    }

    private List<AiSuggestedDoctorResponse> appendSuggestedDoctor(List<AiSuggestedDoctorResponse> current,
                                                                  AiSuggestedDoctorResponse doctor) {
        List<AiSuggestedDoctorResponse> updated = new ArrayList<>();
        if (current != null) {
            for (AiSuggestedDoctorResponse item : current) {
                if (item != null && item.getDoctorId() != null && !item.getDoctorId().equals(doctor.getDoctorId())) {
                    updated.add(item);
                }
            }
        }
        updated.add(doctor);
        return updated.stream()
                .filter(Objects::nonNull)
                .skip(Math.max(0, updated.size() - 5))
                .toList();
    }

    private AiConversationContext resolveContext(PublicAssistantRequest request) {
        String conversationId = blankToNull(request != null ? request.getConversationId() : null);
        AiConversationContext provided = request != null ? request.getContext() : null;
        if (conversationId == null && provided != null) {
            conversationId = blankToNull(provided.getConversationId());
        }

        AiConversationContext cached = conversationId == null ? null : contextCache.getIfPresent(conversationId);
        AiConversationContext context = provided != null ? provided : cached;
        if (context == null) {
            context = new AiConversationContext();
        }
        if (conversationId == null) {
            conversationId = ensureConversationId(context);
        }
        context.setConversationId(conversationId);

        if (provided != null && cached != null) {
            mergeMissingContext(provided, cached);
        }
        return context;
    }

    private void mergeMissingContext(AiConversationContext target, AiConversationContext source) {
        if (target.getCurrentSpecialtyId() == null) {
            target.setCurrentSpecialtyId(source.getCurrentSpecialtyId());
            target.setCurrentSpecialtyName(source.getCurrentSpecialtyName());
        }
        if (target.getCurrentDoctorId() == null) {
            target.setCurrentDoctorId(source.getCurrentDoctorId());
            target.setCurrentDoctorName(source.getCurrentDoctorName());
        }
        if (target.getCurrentFacilityId() == null) {
            target.setCurrentFacilityId(source.getCurrentFacilityId());
            target.setCurrentFacilityName(source.getCurrentFacilityName());
            target.setCurrentFacilityAddress(source.getCurrentFacilityAddress());
        }
        if (target.getLastAvailableSlots() == null || target.getLastAvailableSlots().isEmpty()) {
            target.setLastAvailableSlots(source.getLastAvailableSlots());
        }
        if (target.getLastSuggestedDoctors() == null || target.getLastSuggestedDoctors().isEmpty()) {
            target.setLastSuggestedDoctors(source.getLastSuggestedDoctors());
        }
        if (target.getLastShownDate() == null) {
            target.setLastShownDate(source.getLastShownDate());
        }
        if (target.getPreferredDate() == null) {
            target.setPreferredDate(source.getPreferredDate());
        }
        if (target.getPreferredFromDate() == null) {
            target.setPreferredFromDate(source.getPreferredFromDate());
        }
        if (target.getPreferredWeekday() == null) {
            target.setPreferredWeekday(source.getPreferredWeekday());
        }
        if (target.getPreferredAfterTime() == null) {
            target.setPreferredAfterTime(source.getPreferredAfterTime());
        }
        if (target.getPreferredBeforeTime() == null) {
            target.setPreferredBeforeTime(source.getPreferredBeforeTime());
        }
        if (target.getUserTimePreference() == null) {
            target.setUserTimePreference(source.getUserTimePreference());
        }
        if (target.getPendingBookingDraft() == null) {
            target.setPendingBookingDraft(source.getPendingBookingDraft());
        }
    }

    private String ensureConversationId(AiConversationContext context) {
        if (context != null && blankToNull(context.getConversationId()) != null) {
            return context.getConversationId().trim();
        }
        return UUID.randomUUID().toString();
    }

    private List<PublicAssistantActionResponse> slotActions(List<AiAvailableSlotResponse> slots, boolean includeSecondary) {
        List<PublicAssistantActionResponse> actions = new ArrayList<>();
        if (slots != null) {
            for (AiAvailableSlotResponse slot : slots.stream().limit(MAX_INITIAL_SLOTS).toList()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("slotId", slot.getSlotId());
                payload.put("slot", slot);
                actions.add(action(slot.getDisplayLabel(), "SELECT_SLOT", slot.getSlotId(), payload));
            }
        }
        if (includeSecondary) {
            actions.add(viewMoreSlotsAction());
            actions.add(changeDoctorAction());
            actions.add(action("Xem địa chỉ cơ sở", "VIEW_FACILITY_INFO", null, Map.of()));
        }
        return actions;
    }

    private PublicAssistantActionResponse viewMoreSlotsAction() {
        return action("Xem thêm ca", "VIEW_MORE_SLOTS", null, Map.of());
    }

    private PublicAssistantActionResponse changeDoctorAction() {
        return action("Đổi bác sĩ", "CHANGE_DOCTOR", null, Map.of());
    }

    private List<PublicAssistantActionResponse> branchActions(List<Branch> branches, Specialty specialty) {
        return branchActions(branches, specialty, true);
    }

    private List<PublicAssistantActionResponse> branchActions(List<Branch> branches, Specialty specialty, boolean includeBookingAction) {
        List<PublicAssistantActionResponse> actions = new ArrayList<>();
        if (branches != null) {
            for (Branch branch : branches.stream().filter(Objects::nonNull).limit(3).toList()) {
                Map<String, Object> branchPayload = new LinkedHashMap<>();
                branchPayload.put("branchId", branch.getId());
                branchPayload.put("facilityId", branch.getId());
                branchPayload.put("branchName", facilityName(branch));
                branchPayload.put("facilityName", facilityName(branch));
                branchPayload.put("address", facilityAddress(branch));
                actions.add(action("Xem " + facilityName(branch), "VIEW_BRANCH", "/branches/" + branch.getId(), branchPayload));

                if (specialty != null) {
                    Map<String, Object> doctorsPayload = new LinkedHashMap<>(branchPayload);
                    doctorsPayload.put("specialtyId", specialty.getId());
                    doctorsPayload.put("specialtyName", specialtyName(specialty));
                    actions.add(action(
                            "Xem bác sĩ " + specialtyName(specialty),
                            "VIEW_DOCTORS",
                            "/doctors?branchId=" + branch.getId() + "&specialtyId=" + specialty.getId(),
                            doctorsPayload
                    ));
                }
            }
        }
        if (includeBookingAction && specialty != null) {
            Map<String, Object> bookingPayload = new LinkedHashMap<>();
            bookingPayload.put("specialtyId", specialty.getId());
            bookingPayload.put("specialtyName", specialtyName(specialty));
            actions.add(action("Đặt lịch khám", "GO_TO_BOOKING", "/booking", bookingPayload));
        }
        return actions.stream()
                .filter(Objects::nonNull)
                .limit(5)
                .toList();
    }

    private List<PublicAssistantActionResponse> doctorActions(List<DoctorProfile> doctors, Specialty specialty) {
        return doctorActions(doctors, specialty, true);
    }

    private List<PublicAssistantActionResponse> doctorActions(List<DoctorProfile> doctors, Specialty specialty, boolean includeBookingAction) {
        List<PublicAssistantActionResponse> actions = new ArrayList<>();
        if (doctors != null) {
            for (DoctorProfile doctor : doctors.stream().filter(Objects::nonNull).limit(4).toList()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("doctorId", doctor.getId());
                payload.put("doctorName", doctor.getFullName());
                if (doctor.getBranch() != null) {
                    payload.put("branchId", doctor.getBranch().getId());
                    payload.put("facilityId", doctor.getBranch().getId());
                    payload.put("branchName", facilityName(doctor.getBranch()));
                    payload.put("facilityName", facilityName(doctor.getBranch()));
                }
                Specialty actionSpecialty = specialty != null
                        ? specialty
                        : resolveDoctorSpecialty(doctor, null).orElse(null);
                if (actionSpecialty != null) {
                    payload.put("specialtyId", actionSpecialty.getId());
                    payload.put("specialtyName", specialtyName(actionSpecialty));
                }
                actions.add(action("Xem " + doctor.getFullName(), "VIEW_DOCTOR", "/doctors/" + doctor.getId(), payload));
            }
        }
        if (includeBookingAction && specialty != null) {
            Map<String, Object> bookingPayload = new LinkedHashMap<>();
            bookingPayload.put("specialtyId", specialty.getId());
            bookingPayload.put("specialtyName", specialtyName(specialty));
            actions.add(action("Đặt lịch khám", "GO_TO_BOOKING", "/booking", bookingPayload));
        }
        return actions.stream().limit(5).toList();
    }

    private PublicAssistantActionResponse goToBookingAction(AiBookingDraftResponse bookingDraft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingDraft", bookingDraft);
        return action("Tiếp tục đặt lịch", "GO_TO_BOOKING", "/booking", payload);
    }

    private PublicAssistantActionResponse action(String label, String type, String value, Map<String, Object> payload) {
        return PublicAssistantActionResponse.builder()
                .label(label)
                .type(type)
                .value(value)
                .payload(payload)
                .build();
    }

    private AiSuggestedDoctorResponse suggestedDoctor(DoctorProfile doctor, Specialty specialty) {
        Branch branch = doctor.getBranch();
        return AiSuggestedDoctorResponse.builder()
                .doctorId(doctor.getId())
                .doctorName(doctor.getFullName())
                .specialtyId(specialty.getId())
                .specialtyName(specialtyName(specialty))
                .facilityId(branch != null ? branch.getId() : null)
                .facilityName(branch != null ? facilityName(branch) : null)
                .facilityAddress(branch != null ? facilityAddress(branch) : null)
                .displayTitle(firstNonBlank(doctor.getDisplayTitleVn(), doctor.getDisplayTitleEn(), null))
                .build();
    }

    private AiSuggestedDoctorResponse currentSuggestedDoctor(AiConversationContext context) {
        if (context == null || context.getCurrentDoctorId() == null) {
            return null;
        }
        return AiSuggestedDoctorResponse.builder()
                .doctorId(context.getCurrentDoctorId())
                .doctorName(context.getCurrentDoctorName())
                .specialtyId(context.getCurrentSpecialtyId())
                .specialtyName(context.getCurrentSpecialtyName())
                .facilityId(context.getCurrentFacilityId())
                .facilityName(context.getCurrentFacilityName())
                .facilityAddress(context.getCurrentFacilityAddress())
                .build();
    }

    private AiBookingDraftResponse bookingDraft(AiAvailableSlotResponse slot) {
        return AiBookingDraftResponse.builder()
                .source("AI_ASSISTANT")
                .slotId(slot.getSlotId())
                .doctorId(slot.getDoctorId())
                .doctorName(slot.getDoctorName())
                .specialtyId(slot.getSpecialtyId())
                .specialtyName(slot.getSpecialtyName())
                .facilityId(slot.getFacilityId())
                .branchId(slot.getFacilityId())
                .facilityName(slot.getFacilityName())
                .facilityAddress(slot.getFacilityAddress())
                .appointmentDate(slot.getAppointmentDate())
                .visitDate(slot.getAppointmentDate())
                .session(slot.getSession())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .slotStart(slot.getStartTime())
                .build();
    }

    private String introForSpecialty(Specialty specialty,
                                     DoctorProfile doctor,
                                     List<AiAvailableSlotResponse> slots,
                                     boolean expertiseMatched,
                                     AssistantSpecialtyGroup specialtyGroup) {
        String reason = expertiseMatched
                ? "PrimeCare AI gợi ý bác sĩ này vì bác sĩ có chuyên môn liên quan đến "
                + focusLabel(specialtyGroup, specialty) + " và hiện có lịch trống phù hợp."
                : "PrimeCare AI gợi ý bác sĩ này vì phù hợp chuyên khoa và hiện có lịch trống gần nhất.";
        return "Tôi gợi ý bạn khám " + specialtyName(specialty) + ".\n"
                + reason + "\n"
                + "Bác sĩ phù hợp hiện có lịch là " + doctor.getFullName()
                + " tại " + facilityName(doctor.getBranch()) + ". PrimeCare AI đã tìm thấy một số lịch trống phù hợp cho bạn.";
    }

    private String focusLabel(AssistantSpecialtyGroup specialtyGroup, Specialty specialty) {
        if (specialtyGroup == null) {
            return specialtyName(specialty);
        }
        return switch (specialtyGroup) {
            case DIGESTIVE -> "vấn đề dạ dày/tiêu hóa";
            case CARDIO -> "vấn đề tim mạch";
            case DERMA -> "vấn đề da liễu";
            case DENTAL -> "vấn đề răng hàm mặt";
            case EYE -> "vấn đề về mắt";
            case ENT -> "vấn đề tai mũi họng";
            case ORTHO -> "vấn đề cơ xương khớp";
            case PEDIATRIC -> "khám nhi";
            case OBGYN -> "sản phụ khoa";
            case GENERAL -> "khám tổng quát";
        };
    }

    private String introForDoctor(DoctorProfile doctor, List<AiAvailableSlotResponse> slots) {
        return "PrimeCare AI đã tìm thấy lịch trống gần nhất cho "
                + doctor.getFullName() + " tại " + facilityName(doctor.getBranch()) + ".";
    }

    private String slotsLine(List<AiAvailableSlotResponse> slots) {
        if (slots == null || slots.isEmpty()) {
            return "Chưa có ca trống phù hợp.";
        }
        LocalDate date = slots.get(0).getAppointmentDate();
        String slotLabels = slots.stream()
                .map(AiAvailableSlotResponse::getDisplayLabel)
                .filter(Objects::nonNull)
                .map(label -> "[" + label + "]")
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return "Lịch gần nhất: " + formatDate(date) + "\n" + slotLabels;
    }

    private String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        String day = switch (date.getDayOfWeek()) {
            case MONDAY -> "Thứ Hai";
            case TUESDAY -> "Thứ Ba";
            case WEDNESDAY -> "Thứ Tư";
            case THURSDAY -> "Thứ Năm";
            case FRIDAY -> "Thứ Sáu";
            case SATURDAY -> "Thứ Bảy";
            case SUNDAY -> "Chủ Nhật";
        };
        return day + ", " + date.format(DATE_FORMATTER);
    }

    private String specialtyName(Specialty specialty) {
        return firstNonBlank(specialty.getNameVn(), specialty.getNameEn(), specialty.getCode());
    }

    private String facilityName(Branch branch) {
        return branch == null ? null : firstNonBlank(branch.getNameVn(), branch.getNameEn(), branch.getCode());
    }

    private String facilityAddress(Branch branch) {
        return branch == null ? null : firstNonBlank(branch.getAddressVn(), branch.getAddressEn(), null);
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (blankToNull(first) != null) {
            return first.trim();
        }
        if (blankToNull(second) != null) {
            return second.trim();
        }
        return fallback;
    }

    private String formatTime(LocalTime value) {
        return value == null ? null : value.format(TIME_FORMATTER);
    }

    private int compareSlots(AiAvailableSlotResponse left, AiAvailableSlotResponse right) {
        int byDate = Comparator.nullsLast(LocalDate::compareTo).compare(left.getAppointmentDate(), right.getAppointmentDate());
        if (byDate != 0) {
            return byDate;
        }
        return Comparator.nullsLast(LocalTime::compareTo).compare(parseSlotTime(left.getStartTime()), parseSlotTime(right.getStartTime()));
    }

    private LocalTime parseSlotTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String slotId(Long facilityId, Long specialtyId, Long doctorId, LocalDate date, BranchSessionType session, LocalTime startTime) {
        return String.join("|",
                "SLOT",
                Objects.toString(facilityId, ""),
                Objects.toString(specialtyId, ""),
                Objects.toString(doctorId, ""),
                Objects.toString(date, ""),
                session == null ? "" : session.name(),
                formatTime(startTime)
        );
    }

    private Optional<SlotKey> parseSlotId(String slotId) {
        if (slotId == null || slotId.isBlank()) {
            return Optional.empty();
        }
        String[] parts = slotId.split("\\|");
        if (parts.length != 7 || !"SLOT".equals(parts[0])) {
            return Optional.empty();
        }
        try {
            Long facilityId = Long.parseLong(parts[1]);
            Long specialtyId = Long.parseLong(parts[2]);
            Long doctorId = Long.parseLong(parts[3]);
            if (facilityId <= 0 || specialtyId <= 0 || doctorId <= 0) {
                return Optional.empty();
            }
            LocalDate date = LocalDate.parse(parts[4]);
            BranchSessionType session = BranchSessionType.valueOf(parts[5]);
            LocalTime start = LocalTime.parse(parts[6], TIME_FORMATTER);
            return Optional.of(new SlotKey(slotId, facilityId, specialtyId, doctorId, date, session, start));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private SlotKey keyFromSlot(AiAvailableSlotResponse slot) {
        if (slot == null) {
            return null;
        }
        try {
            return new SlotKey(
                    slot.getSlotId(),
                    slot.getFacilityId(),
                    slot.getSpecialtyId(),
                    slot.getDoctorId(),
                    slot.getAppointmentDate(),
                    BranchSessionType.valueOf(slot.getSession()),
                    LocalTime.parse(slot.getStartTime(), TIME_FORMATTER)
            );
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Optional<AiAvailableSlotResponse> findSlotInContext(AiConversationContext context, String slotId) {
        if (context == null || context.getLastAvailableSlots() == null || slotId == null) {
            return Optional.empty();
        }
        return context.getLastAvailableSlots().stream()
                .filter(slot -> slotId.equals(slot.getSlotId()))
                .findFirst();
    }

    private String normalizeActionType(String actionType) {
        return blankToNull(actionType) == null ? null : actionType.trim().toUpperCase(Locale.ROOT);
    }

    private String slotIdFromPayload(PublicAssistantRequest request) {
        if (request == null || request.getActionPayload() == null) {
            return null;
        }
        return slotIdFromPayload(request.getActionPayload());
    }

    private String slotIdFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get("slotId");
        return value == null ? null : blankToNull(Objects.toString(value, null));
    }

    private Map<String, Object> slotToolMap(AiAvailableSlotResponse slot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slotId", slot.getSlotId());
        map.put("doctorId", slot.getDoctorId());
        map.put("doctorName", slot.getDoctorName());
        map.put("specialtyId", slot.getSpecialtyId());
        map.put("specialtyName", slot.getSpecialtyName());
        map.put("facilityId", slot.getFacilityId());
        map.put("facilityName", slot.getFacilityName());
        map.put("facilityAddress", slot.getFacilityAddress());
        map.put("appointmentDate", slot.getAppointmentDate().toString());
        map.put("session", slot.getSession());
        map.put("startTime", slot.getStartTime());
        map.put("endTime", slot.getEndTime());
        map.put("displayLabel", slot.getDisplayLabel());
        return map;
    }

    private record DoctorSlots(DoctorProfile doctor, List<AiAvailableSlotResponse> slots, int score, boolean expertiseMatched) {
        AiAvailableSlotResponse firstSlot() {
            return slots.get(0);
        }
    }

    private record TimePreference(String label, LocalTime notBefore, LocalTime before) {
    }

    private record SpecialtyResolution(Specialty specialty, boolean symptom, AssistantSpecialtyGroup symptomGroup) {
    }

    private record SlotSelectionMatch(boolean attempted, AiAvailableSlotResponse slot, boolean ambiguous) {
    }

    private record SlotKey(String slotId,
                           Long facilityId,
                           Long specialtyId,
                           Long doctorId,
                           LocalDate appointmentDate,
                           BranchSessionType session,
                           LocalTime startTime) {
    }

    private record SlotValidation(boolean available,
                                  AiAvailableSlotResponse slot,
                                  AiSuggestedDoctorResponse suggestedDoctor,
                                  List<AiAvailableSlotResponse> alternativeSlots) {
    }

    private record ScoredDoctor(DoctorProfile doctor, int score) {
    }

    private record ScoredMedicalService(MedicalService service, int score) {
    }

    private record PublicServiceMatch(String keyword, List<MedicalService> matchedServices) {
    }

    private record PublicAssistantQueryPlan(PublicAssistantQueryIntent intent,
                                            String normalizedText,
                                            String rawText,
                                            boolean countQuestion,
                                            boolean followUpQuestion,
                                            boolean infoQuestion,
                                            boolean availabilityQuestion,
                                            boolean bookingQuestion,
                                            String doctorNameQuery,
                                            PublicLocationMatch location,
                                            Specialty specialty,
                                            PublicServiceMatch serviceMatch,
                                            List<DoctorProfile> doctorMatches,
                                            List<Branch> activeBranches,
                                            LocalDate datePreference,
                                            TimePreference timePreference) {
    }

    private record PublicLocationMatch(String keyword,
                                       String displayLabel,
                                       List<Branch> matchedBranches) {
        boolean hasLocationSignal() {
            return blankToNull(keyword) != null || (matchedBranches != null && !matchedBranches.isEmpty());
        }

        boolean matches(Branch branch) {
            if (branch == null) {
                return false;
            }
            if (matchedBranches != null && matchedBranches.stream().anyMatch(item -> Objects.equals(item.getId(), branch.getId()))) {
                return true;
            }
            String normalizedKeyword = canonicalLocationKeyword(searchable(keyword));
            if (normalizedKeyword.isBlank()) {
                return false;
            }
            String corpus = searchable(String.join(" ",
                    Objects.toString(branch.getCode(), ""),
                    Objects.toString(branch.getNameVn(), ""),
                    Objects.toString(branch.getNameEn(), ""),
                    Objects.toString(branch.getAddressVn(), ""),
                    Objects.toString(branch.getAddressEn(), "")
            ));
            return corpus.contains(normalizedKeyword);
        }

        String displayLabelOr(String fallback) {
            return blankToNull(displayLabel) != null ? displayLabel.trim() : fallback;
        }
    }

    private enum AssistantRouteIntent {
        BOOKING,
        SYSTEM_FAQ,
        OUT_OF_SCOPE
    }

    private enum PublicAssistantQueryIntent {
        FOLLOW_UP_BRANCH_INFO,
        FOLLOW_UP_DOCTOR_INFO,
        BRANCH_COUNT,
        BRANCH_INFO,
        BRANCH_SPECIALTY_INFO,
        BRANCH_SERVICE_INFO,
        DOCTOR_COUNT,
        DOCTOR_NAME_LOOKUP,
        DOCTOR_LIST_BY_BRANCH,
        DOCTOR_LIST_BY_SPECIALTY_LOCATION,
        SPECIALTY_INFO,
        SERVICE_INFO,
        SYMPTOM_GUIDANCE,
        AVAILABILITY_SEARCH,
        BOOKING_DRAFT,
        FAQ_OR_FALLBACK
    }

    private enum AssistantSpecialtyGroup {
        DIGESTIVE(
                List.of("dau da day", "da day", "dau bung", "trao nguoc", "o nong", "day hoi", "kho tieu", "tieu chay", "tao bon", "buon non", "non", "dai trang", "roi loan tieu hoa"),
                List.of("tieu hoa", "gastro", "da day", "khoa tieu hoa", "kham tieu hoa"),
                List.of("GASTRO"),
                List.of("GENERAL")
        ),
        CARDIO(
                List.of("dau nguc", "tuc nguc", "hoi hop", "tim dap nhanh", "huyet ap", "kho tho", "tim mach", "dau vung tim"),
                List.of("tim mach", "cardio", "cardiology", "khoa tim mach"),
                List.of("CARDIO"),
                List.of("GENERAL")
        ),
        DERMA(
                List.of("noi man", "man ngua", "ngua da", "phat ban", "di ung da", "viem da", "mun", "noi me day", "bong troc da"),
                List.of("da lieu", "derma", "dermatology", "khoa da lieu"),
                List.of("DERMA"),
                List.of("GENERAL")
        ),
        DENTAL(
                List.of("dau rang", "sau rang", "e buot rang", "viem nuou", "chay mau chan rang", "sung loi", "rang khon", "nho rang", "nuou", "dau loi"),
                List.of("rang ham mat", "nha khoa", "dental", "oral", "khoa rang ham mat", "kham rang"),
                List.of("DENTAL", "ORAL"),
                List.of("GENERAL")
        ),
        EYE(
                List.of("dau mat", "do mat", "nhin mo", "kho mat", "com mat", "chay nuoc mat", "ngua mat", "moi mat", "giam thi luc"),
                List.of("kham mat", "khoa mat", "chuyen khoa mat", "nhan khoa", "ophthalmology", "eye"),
                List.of("EYE"),
                List.of("GENERAL")
        ),
        ENT(
                List.of("dau hong", "viem hong", "bi ho", "toi ho", "ho nhieu", "ho keo dai", "nghet mui", "so mui", "viem xoang", "u tai", "dau tai", "tai mui hong"),
                List.of("tai mui hong", "ent", "khoa tai mui hong"),
                List.of("ENT"),
                List.of("GENERAL")
        ),
        ORTHO(
                List.of("dau lung", "dau goi", "dau vai gay", "dau co", "dau khop", "xuong khop", "chan thuong", "bong gan", "trat khop", "dau tay", "dau chan", "co xuong khop"),
                List.of("co xuong khop", "xuong khop", "chan thuong chinh hinh", "orthopedics", "ortho"),
                List.of("ORTHO"),
                List.of("GENERAL")
        ),
        PEDIATRIC(
                List.of("con toi", "con em", "be", "be nha toi", "tre", "tre em", "em be", "chau", "be trai", "be gai", "be bi", "tre bi"),
                List.of("nhi", "nhi khoa", "khoa nhi", "bac si nhi", "pediatrics", "pediatric", "kham nhi"),
                List.of("PED"),
                List.of("GENERAL")
        ),
        OBGYN(
                List.of("mang thai", "co bau", "thai", "kham thai", "phu khoa", "kinh nguyet", "rong kinh", "dau bung kinh", "khi hu", "san phu khoa"),
                List.of("san phu khoa", "san", "phu khoa", "nu khoa", "obgyn", "obstetrics", "gynecology"),
                List.of("OBGYN"),
                List.of("GENERAL")
        ),
        GENERAL(
                List.of("sot", "met", "met moi", "khong khoe", "dau dau", "chong mat", "mat ngu", "kham tong quat", "kiem tra suc khoe", "suy nhuoc"),
                List.of("noi tong quat", "tong quat", "general", "kham tong quat"),
                List.of("GENERAL"),
                List.of()
        );

        private final List<String> symptomKeywords;
        private final List<String> directAliases;
        private final List<String> preferredCodes;
        private final List<String> fallbackCodes;

        AssistantSpecialtyGroup(List<String> symptomKeywords,
                                List<String> directAliases,
                                List<String> preferredCodes,
                                List<String> fallbackCodes) {
            this.symptomKeywords = symptomKeywords;
            this.directAliases = directAliases;
            this.preferredCodes = preferredCodes;
            this.fallbackCodes = fallbackCodes;
        }

        List<String> symptomKeywords() {
            return symptomKeywords;
        }

        List<String> directAliases() {
            return directAliases;
        }

        List<String> preferredCodes() {
            return preferredCodes;
        }

        List<String> fallbackCodes() {
            return fallbackCodes;
        }
    }
}
