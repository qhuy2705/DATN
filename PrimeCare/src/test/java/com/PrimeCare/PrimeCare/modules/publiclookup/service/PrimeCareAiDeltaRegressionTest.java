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
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.BranchSpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.medical_service.repository.MedicalServiceRepository;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiAvailableSlotResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiConversationContext;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantResponse;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrimeCareAiDeltaRegressionTest {

    private static final Long BRANCH_ID = 1L;
    private static final Long GASTRO_ID = 10L;
    private static final Long DENTAL_ID = 11L;
    private static final Long EYE_ID = 12L;
    private static final Long ENT_ID = 13L;
    private static final Long PED_ID = 14L;
    private static final Long DERMA_ID = 15L;
    private static final Long GENERAL_ID = 16L;

    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private BranchSpecialtyRepository branchSpecialtyRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    @Mock
    private AppointmentAvailabilityService appointmentAvailabilityService;
    @Mock
    private MedicalServiceRepository medicalServiceRepository;

    @InjectMocks
    private AiBookingAssistantService service;

    @BeforeEach
    void allowPublicBranchSpecialtyMappings() {
        when(branchSpecialtyRepository.existsByBranch_IdAndSpecialty_IdAndStatus(any(), any(), eq(BranchSpecialtyStatus.ACTIVE)))
                .thenReturn(true);
    }

    @Test
    void brandAndCommonMedicationQuestionsAreHardBlockedWithCleanShape() {
        for (String question : List.of(
                "Hapacol được không?",
                "Đau bụng uống Nospa được không?",
                "Tôi nên mua thuốc gì?",
                "Có nên dùng thuốc nhỏ mắt không?",
                "Bị ho dùng Eugica được không?",
                "Đau dạ dày uống Maalox được không?",
                "Dị ứng uống Cetirizine được không?"
        )) {
            PublicAssistantResponse response = service.chat(request(question, null));

            assertThat(response.getIntent()).isEqualTo("MEDICATION_SAFETY_BLOCK");
            assertSafetyShape(response);
        }
        verifyNoInteractions(specialtyRepository, doctorProfileRepository, branchRepository, doctorWorkScheduleRepository, appointmentAvailabilityService);
    }

    @Test
    void medicationWorkflowQuestionsAreNotMedicationHardBlocked() {
        assertThat(PublicAssistantSafety.detect("Nhà thuốc PrimeCare ở đâu?")).isEmpty();
        assertThat(PublicAssistantSafety.detect("Tôi muốn xem đơn thuốc cũ")).isEmpty();
        assertThat(PublicAssistantSafety.detect("Bác sĩ có kê đơn sau khi khám không?")).isEmpty();
        assertThat(service.tryHandle(request("Bác sĩ có kê đơn sau khi khám không?", null))).isEmpty();
    }

    @Test
    void suspicionDiagnosisQuestionsAreHardBlocked() {
        for (String question : List.of(
                "Liệu tôi có mắc bệnh gì không?",
                "Có khả năng là bệnh gì?",
                "Khả năng cao là gì?",
                "Dấu hiệu này giống bệnh gì?",
                "Tình trạng này có đáng lo không?",
                "Như vậy có nguy hiểm không?",
                "Tôi có cần đi cấp cứu không?",
                "Có phải dấu hiệu ung thư không?",
                "Có phải dấu hiệu viêm ruột thừa không?",
                "Có phải dấu hiệu đột quỵ không?",
                "Có phải dấu hiệu nhồi máu cơ tim không?"
        )) {
            PublicAssistantResponse response = service.chat(request(question, null));

            assertThat(response.getIntent()).isEqualTo("DIAGNOSIS_SAFETY_BLOCK");
            assertSafetyShape(response);
        }
    }

    @Test
    void systemRoutingDoesNotUseShortSubstringMatches() {
        for (String question : List.of(
                "Trang chủ ở đâu?",
                "Trang tra cứu của tôi lỗi",
                "Tôi quên mật khẩu",
                "Mật khẩu không đúng",
                "Hồ sơ bệnh án ở đâu?",
                "Tôi hỏi về bảo hiểm",
                "Tôi muốn xem kết quả xét nghiệm",
                "Tôi cần hỗ trợ tài khoản",
                "Có thể đổi số điện thoại không?",
                "Tôi muốn cập nhật email"
        )) {
            assertThat(service.tryHandle(request(question, null))).as(question).isEmpty();
        }
        verifyNoInteractions(specialtyRepository, doctorProfileRepository, branchRepository, doctorWorkScheduleRepository, appointmentAvailabilityService);
    }

    @Test
    void clearBookingQuestionsStillRouteToBooking() {
        Specialty gastro = specialty(GASTRO_ID, "GASTRO", "Tiêu hóa");
        Specialty dental = specialty(DENTAL_ID, "DENTAL", "Răng Hàm Mặt");
        Specialty eye = specialty(EYE_ID, "EYE", "Mắt");
        Specialty ent = specialty(ENT_ID, "ENT", "Tai Mũi Họng");
        Specialty ped = specialty(PED_ID, "PED", "Nhi khoa");
        Specialty derma = specialty(DERMA_ID, "DERMA", "Da liễu");
        stubMappedDoctors(gastro, dental, eye, ent, ped, derma);

        assertThat(service.tryHandle(request("Tôi đau răng", null))).isPresent();
        assertThat(service.tryHandle(request("Tôi đau mắt", null))).isPresent();
        assertThat(service.tryHandle(request("Tôi bị ho nhiều", null))).isPresent();
        assertThat(service.tryHandle(request("Bé nhà tôi bị sốt", null))).isPresent();
        assertThat(service.tryHandle(request("Tôi muốn khám da liễu", null))).isPresent();
        assertThat(service.tryHandle(request("Có bác sĩ tiêu hóa không?", null))).isPresent();
    }

    @Test
    void ambiguousSymptomsDoNotGuessNarrowSpecialty() {
        PublicAssistantResponse pain = service.chat(request("Tôi đau", null));
        assertThat(pain.getIntent()).isEqualTo("CLARIFICATION");
        assertThat(pain.getMessage()).contains("vị trí đau");
        assertThat(pain.getSuggestedDoctor()).isNull();
        assertThat(pain.getAvailableSlots()).isEmpty();
        assertThat(pain.getActions()).isEmpty();
        assertThat(pain.getSuggestions()).isEmpty();

        PublicAssistantResponse strange = service.chat(request("Tôi thấy lạ lắm", null));
        assertThat(strange.getIntent()).isEqualTo("CLARIFICATION");
        assertThat(strange.getSuggestedDoctor()).isNull();

        Specialty general = specialty(GENERAL_ID, "GENERAL", "Nội tổng quát");
        stubMappedDoctors(general);
        PublicAssistantResponse tired = service.chat(request("Tôi mệt", null));
        assertThat(tired.getContext().getCurrentSpecialtyId()).isEqualTo(GENERAL_ID);
        PublicAssistantResponse unwell = service.chat(request("Tôi không khỏe", null));
        assertThat(unwell.getContext().getCurrentSpecialtyId()).isEqualTo(GENERAL_ID);
    }

    @Test
    void viewMoreSlotsKeepsTomorrowDatePreference() {
        Specialty gastro = specialty(GASTRO_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(101L, "Bác sĩ Tiêu Hóa", gastro);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        stubSchedulesAndSlots(doctor, gastro, Map.of(
                tomorrow, List.of(
                        slot(LocalTime.of(8, 0)),
                        slot(LocalTime.of(8, 30)),
                        slot(LocalTime.of(9, 0)),
                        slot(LocalTime.of(9, 30))
                )
        ));
        PublicAssistantResponse first = service.chat(request("Khám tiêu hóa", null));
        PublicAssistantResponse tomorrowResponse = service.chat(request("Ngày mai có không?", first.getContext()));

        PublicAssistantResponse more = service.chat(request("Xem thêm ca", tomorrowResponse.getContext()));

        assertThat(more.getAvailableSlots()).isNotEmpty();
        assertThat(more.getAvailableSlots()).allSatisfy(slot ->
                assertThat(slot.getAppointmentDate()).isEqualTo(tomorrow));
    }

    @Test
    void viewMoreSlotsKeepsAfterTwoPmTomorrowPreference() {
        Specialty gastro = specialty(GASTRO_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(102L, "Bác sĩ Tiêu Hóa", gastro);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        stubSchedulesAndSlots(doctor, gastro, Map.of(
                tomorrow, List.of(
                        slot(LocalTime.of(13, 30)),
                        slot(LocalTime.of(14, 0)),
                        slot(LocalTime.of(14, 30)),
                        slot(LocalTime.of(15, 0)),
                        slot(LocalTime.of(15, 30))
                )
        ));
        PublicAssistantResponse first = service.chat(request("Khám tiêu hóa", null));
        PublicAssistantResponse afterTwo = service.chat(request("Sau 14h ngày mai có không?", first.getContext()));

        PublicAssistantResponse more = service.chat(request("Xem thêm ca", afterTwo.getContext()));

        assertThat(more.getAvailableSlots()).isNotEmpty();
        assertThat(more.getAvailableSlots()).allSatisfy(slot -> {
            assertThat(slot.getAppointmentDate()).isEqualTo(tomorrow);
            assertThat(LocalTime.parse(slot.getStartTime())).isAfterOrEqualTo(LocalTime.of(14, 0));
        });
        assertThat(more.getContext().getPreferredAfterTime()).isEqualTo("14:00");
    }

    @Test
    void viewMoreSlotsKeepsWeekdayPreference() {
        Specialty gastro = specialty(GASTRO_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(103L, "Bác sĩ Tiêu Hóa", gastro);
        LocalDate nextFriday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        stubSchedulesAndSlots(doctor, gastro, Map.of(
                nextFriday, List.of(
                        slot(LocalTime.of(8, 0)),
                        slot(LocalTime.of(8, 30)),
                        slot(LocalTime.of(9, 0)),
                        slot(LocalTime.of(9, 30))
                )
        ));
        PublicAssistantResponse first = service.chat(request("Khám tiêu hóa", null));
        PublicAssistantResponse friday = service.chat(request("Thứ 6 có không?", first.getContext()));

        PublicAssistantResponse more = service.chat(request("Xem thêm ca", friday.getContext()));

        assertThat(more.getAvailableSlots()).isNotEmpty();
        assertThat(more.getAvailableSlots()).allSatisfy(slot ->
                assertThat(slot.getAppointmentDate()).isEqualTo(nextFriday));
        assertThat(more.getContext().getPreferredWeekday()).isEqualTo(DayOfWeek.FRIDAY.name());
    }

    @Test
    void recommendationMessageDoesNotUseOverclaimWording() {
        Specialty gastro = specialty(GASTRO_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(104L, "Bác sĩ Tiêu Hóa", gastro);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        stubSchedulesAndSlots(doctor, gastro, Map.of(
                LocalDate.now().plusDays(1), List.of(slot(LocalTime.of(8, 0)))
        ));

        PublicAssistantResponse response = service.chat(request("Tôi đau dạ dày", null));

        assertThat(response.getMessage())
                .doesNotContain("bác sĩ tốt nhất")
                .doesNotContain("phù hợp nhất tuyệt đối")
                .doesNotContain("chắc chắn là bác sĩ phù hợp nhất")
                .doesNotContain("điều trị tốt nhất");
    }

    private void assertSafetyShape(PublicAssistantResponse response) {
        assertThat(response.getProvider()).isEqualTo("PrimeCare AI");
        assertThat(response.getSuggestedDoctor()).isNull();
        assertThat(response.getAvailableSlots()).isEmpty();
        assertThat(response.getBookingDraft()).isNull();
        assertThat(response.getActions()).isEmpty();
        assertThat(response.getSuggestions()).isEmpty();
    }

    private PublicAssistantRequest request(String question, AiConversationContext context) {
        PublicAssistantRequest request = new PublicAssistantRequest();
        request.setQuestion(question);
        request.setContext(context);
        request.setConversationId(context != null ? context.getConversationId() : null);
        return request;
    }

    private void stubMappedDoctors(Specialty... specialties) {
        stubSpecialties(specialties);
        Map<Long, DoctorProfile> doctorsBySpecialty = new LinkedHashMap<>();
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Map<LocalDate, List<BookableSlotResponse>> slotsByDate = Map.of(
                tomorrow, List.of(slot(LocalTime.of(8, 0)), slot(LocalTime.of(8, 30)), slot(LocalTime.of(9, 0)))
        );
        for (Specialty specialty : specialties) {
            DoctorProfile doctor = doctor(1000L + specialty.getId(), "Bác sĩ " + specialty.getCode(), specialty);
            doctorsBySpecialty.put(specialty.getId(), doctor);
            stubSchedulesAndSlots(doctor, specialty, slotsByDate);
        }
        when(doctorProfileRepository.search(isNull(), any(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Long specialtyId = invocation.getArgument(1);
                    DoctorProfile doctor = doctorsBySpecialty.get(specialtyId);
                    return new PageImpl<>(doctor == null ? List.of() : List.of(doctor));
                });
    }

    private void stubSpecialties(Specialty... specialties) {
        when(specialtyRepository.findAllByStatus(eq("ACTIVE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(specialties)));
        for (Specialty specialty : specialties) {
            when(specialtyRepository.findById(specialty.getId())).thenReturn(Optional.of(specialty));
        }
    }

    private void stubDoctors(Specialty specialty, DoctorProfile doctor) {
        when(doctorProfileRepository.search(isNull(), eq(specialty.getId()), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));
        when(doctorProfileRepository.findPublicBookableById(doctor.getId())).thenReturn(Optional.of(doctor));
    }

    private void stubSchedulesAndSlots(DoctorProfile doctor,
                                       Specialty specialty,
                                       Map<LocalDate, List<BookableSlotResponse>> slotsByDate) {
        when(doctorWorkScheduleRepository.findByDoctor_IdAndWorkDateBetweenOrderByWorkDateAscSessionAsc(
                eq(doctor.getId()),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenAnswer(invocation -> {
            LocalDate from = invocation.getArgument(1);
            LocalDate to = invocation.getArgument(2);
            return slotsByDate.keySet().stream()
                    .filter(date -> !date.isBefore(from) && !date.isAfter(to))
                    .sorted()
                    .map(date -> schedule(doctor, date, BranchSessionType.AM))
                    .toList();
        });
        when(appointmentAvailabilityService.getAvailability(
                eq(BRANCH_ID),
                eq(specialty.getId()),
                eq(doctor.getId()),
                any(LocalDate.class),
                any(BranchSessionType.class),
                anyBoolean()
        )).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(3);
            BranchSessionType session = invocation.getArgument(4);
            return availability(doctor, specialty, date, session, slotsByDate.getOrDefault(date, List.of()));
        });
    }

    private DoctorWorkSchedule schedule(DoctorProfile doctor, LocalDate visitDate, BranchSessionType session) {
        return DoctorWorkSchedule.builder()
                .doctor(doctor)
                .workDate(visitDate)
                .session(session)
                .build();
    }

    private AppointmentAvailabilityResponse availability(DoctorProfile doctor,
                                                         Specialty specialty,
                                                         LocalDate visitDate,
                                                         BranchSessionType session,
                                                         List<BookableSlotResponse> slots) {
        return AppointmentAvailabilityResponse.builder()
                .branchId(BRANCH_ID)
                .branchNameVn("PrimeCare Quận 1")
                .specialtyId(specialty.getId())
                .specialtyNameVn(specialty.getNameVn())
                .doctorId(doctor.getId())
                .doctorName(doctor.getFullName())
                .visitDate(visitDate)
                .session(session)
                .slotMinutes(30)
                .totalSlots(slots.size())
                .remainingSlots(slots.size())
                .slots(slots)
                .build();
    }

    private BookableSlotResponse slot(LocalTime start) {
        return BookableSlotResponse.builder()
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .available(true)
                .capacity(1)
                .remainingSlots(1)
                .build();
    }

    private DoctorProfile doctor(Long id, String name, Specialty specialty) {
        DoctorProfile doctor = DoctorProfile.builder()
                .id(id)
                .fullName(name)
                .branch(branch())
                .status(DoctorStatus.ACTIVE)
                .build();
        doctor.getDoctorSpecialties().add(DoctorSpecialty.builder()
                .doctor(doctor)
                .specialty(specialty)
                .build());
        return doctor;
    }

    private Specialty specialty(Long id, String code, String name) {
        return Specialty.builder()
                .id(id)
                .code(code)
                .nameVn(name)
                .status("ACTIVE")
                .defaultSlotMinutes(30)
                .bufferEveryN(0)
                .bufferMinutes(0)
                .build();
    }

    private Branch branch() {
        return Branch.builder()
                .id(BRANCH_ID)
                .code("Q1")
                .nameVn("PrimeCare Quận 1")
                .addressVn("12 Nguyễn Huệ, Quận 1")
                .status(BranchStatus.ACTIVE)
                .build();
    }
}
