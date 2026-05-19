package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAvailabilityResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.BookableSlotResponse;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
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
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiBookingDraftResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiConversationContext;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantMessageRequest;
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

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiBookingAssistantServiceTest {

    private static final Long BRANCH_ID = 1L;
    private static final Long SPECIALTY_ID = 2L;
    private static final Long DOCTOR_ID = 3L;
    private static final Long OTHER_DOCTOR_ID = 4L;
    private static final LocalDate VISIT_DATE = LocalDate.now().plusDays(1);

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
    private AppointmentRepository appointmentRepository;
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
    void symptomQuestionRecommendsSpecialtyDoctorAndThreeSlotsFromRealAvailability() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        stubSchedulesAndAvailability(doctor, gastro, true, List.of(
                slot(LocalTime.of(8, 30), true),
                slot(LocalTime.of(9, 0), true),
                slot(LocalTime.of(10, 30), true),
                slot(LocalTime.of(11, 0), true)
        ));

        PublicAssistantResponse response = service.chat(request("Tôi hay đau dạ dày", null));

        assertThat(response.getIntent()).isEqualTo("SYMPTOM_TO_SPECIALTY");
        assertThat(response.getProvider()).isEqualTo("PrimeCare AI");
        assertThat(response.getSuggestedDoctor().getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(response.getContext().getCurrentSpecialtyName()).isEqualTo("Tiêu hóa");
        assertThat(response.getAvailableSlots()).hasSize(3);
        assertThat(response.getAvailableSlots()).extracting(AiAvailableSlotResponse::getStartTime)
                .containsExactly("08:30", "09:00", "10:30");
        assertThat(response.getBookingDraft()).isNull();
    }

    @Test
    void earliestFollowUpUsesSpecialtyFromSymptomContext() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        stubSchedulesAndAvailability(doctor, gastro, true, defaultSlots());

        PublicAssistantResponse first = service.chat(request("Tôi đau dạ dày", null));
        PublicAssistantResponse followUp = service.chat(request("Tôi muốn khám sớm nhất", first.getContext()));

        assertThat(followUp.getSuggestedDoctor().getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(followUp.getContext().getCurrentSpecialtyName()).isEqualTo("Tiêu hóa");
        assertThat(followUp.getAvailableSlots()).hasSize(3);
        assertThat(followUp.getBookingDraft()).isNull();
    }

    @Test
    void directSpecialtyQuestionRecommendsDoctorAndThreeSlots() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        stubSchedulesAndAvailability(doctor, gastro, true, List.of(
                slot(LocalTime.of(8, 30), true),
                slot(LocalTime.of(9, 0), true),
                slot(LocalTime.of(10, 30), true)
        ));

        PublicAssistantResponse response = service.chat(request("Tôi muốn khám tiêu hóa", null));

        assertThat(response.getIntent()).isEqualTo("SPECIALTY_SEARCH");
        assertThat(response.getSuggestedDoctor().getSpecialtyId()).isEqualTo(SPECIALTY_ID);
        assertThat(response.getAvailableSlots()).hasSize(3);
    }

    @Test
    void stomachPainMapsToGastroAndNeverPediatricsWhenGastroExists() {
        Specialty gastro = specialty(10L, "GASTRO", "Tiêu hóa");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");
        Specialty pediatrics = specialty(12L, "PED", "Nhi khoa");

        PublicAssistantResponse response = chatWithMappedSpecialties("Tôi đau dạ dày", gastro, gastro, general, pediatrics);

        assertThat(response.getIntent()).isEqualTo("SYMPTOM_TO_SPECIALTY");
        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(gastro.getId());
        assertThat(response.getContext().getCurrentSpecialtyId()).isNotEqualTo(pediatrics.getId());
    }

    @Test
    void stomachPainFallsBackToGeneralAndNeverPediatricsWhenGastroMissing() {
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");
        Specialty pediatrics = specialty(12L, "PED", "Nhi khoa");

        PublicAssistantResponse response = chatWithMappedSpecialties("Tôi đau bụng", general, general, pediatrics);

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(general.getId());
        assertThat(response.getContext().getCurrentSpecialtyId()).isNotEqualTo(pediatrics.getId());
    }

    @Test
    void refluxMapsToGastroOrGeneralAndNeverPediatrics() {
        Specialty gastro = specialty(10L, "GASTRO", "Tiêu hóa");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");
        Specialty pediatrics = specialty(12L, "PED", "Nhi khoa");

        PublicAssistantResponse response = chatWithMappedSpecialties("Tôi bị trào ngược dạ dày", gastro, gastro, general, pediatrics);

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(gastro.getId());
        assertThat(response.getContext().getCurrentSpecialtyId()).isNotEqualTo(pediatrics.getId());
    }

    @Test
    void childStomachPainPrioritizesPediatricsWhenPediatricsExists() {
        Specialty gastro = specialty(10L, "GASTRO", "Tiêu hóa");
        Specialty pediatrics = specialty(12L, "PED", "Nhi khoa");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");

        PublicAssistantResponse response = chatWithMappedSpecialties("Con tôi đau bụng", pediatrics, gastro, pediatrics, general);

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(pediatrics.getId());
        assertThat(response.getContext().getCurrentSpecialtyId()).isNotEqualTo(gastro.getId());
    }

    @Test
    void childFeverMapsToPediatrics() {
        Specialty pediatrics = specialty(12L, "PED", "Nhi khoa");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");

        PublicAssistantResponse response = chatWithMappedSpecialties("Bé nhà tôi bị sốt", pediatrics, pediatrics, general);

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(pediatrics.getId());
    }

    @Test
    void directPediatricsRequestMapsToPediatrics() {
        Specialty pediatrics = specialty(12L, "PED", "Nhi khoa");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");

        PublicAssistantResponse response = chatWithMappedSpecialties("Tôi muốn khám nhi khoa", pediatrics, pediatrics, general);

        assertThat(response.getIntent()).isEqualTo("SPECIALTY_SEARCH");
        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(pediatrics.getId());
    }

    @Test
    void toothPainMapsToDentalAndNeverEyeWhenDentalExists() {
        Specialty dental = specialty(13L, "DENTAL", "Răng Hàm Mặt");
        Specialty eye = specialty(14L, "EYE", "Mắt");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");

        PublicAssistantResponse response = chatWithMappedSpecialties("Tôi đau răng", dental, dental, eye, general);

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(dental.getId());
        assertThat(response.getContext().getCurrentSpecialtyId()).isNotEqualTo(eye.getId());
    }

    @Test
    void toothPainFallsBackToGeneralAndNeverEyeWhenDentalMissing() {
        Specialty eye = specialty(14L, "EYE", "Mắt");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");

        PublicAssistantResponse response = chatWithMappedSpecialties("Tôi đau răng", general, eye, general);

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(general.getId());
        assertThat(response.getContext().getCurrentSpecialtyId()).isNotEqualTo(eye.getId());
    }

    @Test
    void eyePainMapsToEye() {
        Specialty eye = specialty(14L, "EYE", "Mắt");
        Specialty derma = specialty(15L, "DERMA", "Da liễu");
        Specialty pediatrics = specialty(12L, "PED", "Nhi khoa");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");

        PublicAssistantResponse response = chatWithMappedSpecialties("Tôi đau mắt", eye, eye, derma, pediatrics, general);

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(eye.getId());
        assertThat(response.getContext().getCurrentSpecialtyId()).isNotIn(derma.getId(), pediatrics.getId(), general.getId());
    }

    @Test
    void skinRashMapsToDermatology() {
        Specialty derma = specialty(15L, "DERMA", "Da liễu");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");

        PublicAssistantResponse response = chatWithMappedSpecialties("Tôi nổi mẩn da", derma, derma, general);

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(derma.getId());
    }

    @Test
    void shoulderNeckPainMapsToOrthoOrGeneral() {
        Specialty ortho = specialty(17L, "ORTHO", "Chấn thương chỉnh hình");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");

        PublicAssistantResponse response = chatWithMappedSpecialties("Tôi đau vai gáy", ortho, ortho, general);

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(ortho.getId());
    }

    @Test
    void feverWithoutPediatricContextMapsToGeneral() {
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");
        Specialty pediatrics = specialty(12L, "PED", "Nhi khoa");

        PublicAssistantResponse response = chatWithMappedSpecialties("Tôi bị sốt", general, general, pediatrics);

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(general.getId());
        assertThat(response.getContext().getCurrentSpecialtyId()).isNotEqualTo(pediatrics.getId());
    }

    @Test
    void coughMapsToEntWhenClearCoughPhraseIsPresent() {
        Specialty ent = specialty(18L, "ENT", "Tai Mũi Họng");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");

        PublicAssistantResponse response = chatWithMappedSpecialties("Tôi bị ho nhiều", ent, ent, general);

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(ent.getId());
    }

    @Test
    void chestPainMapsToCardiologyWhenNotEmergencyWording() {
        Specialty cardio = specialty(16L, "CARDIO", "Tim mạch");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");

        PublicAssistantResponse response = chatWithMappedSpecialties("Tôi đau ngực", cardio, cardio, general);

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(cardio.getId());
    }

    @Test
    void severeChestPainReturnsSafetyMessageWithoutBookingDraft() {
        Specialty cardio = specialty(16L, "CARDIO", "Tim mạch");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");
        stubSpecialties(cardio, general);

        PublicAssistantResponse response = service.chat(request("Tôi đau ngực dữ dội và khó thở nặng", null));

        assertThat(response.getIntent()).isEqualTo("EMERGENCY_SAFETY_BLOCK");
        assertThat(response.getMessage()).isEqualTo(PublicAssistantSafety.EMERGENCY_BLOCK_MESSAGE);
        assertThat(response.getBookingDraft()).isNull();
    }

    @Test
    void medicationQuestionIsHardBlockedInsideAiBookingEndpoint() {
        PublicAssistantResponse response = service.chat(request("Tôi đau dạ dày uống thuốc gì?", null));

        assertThat(response.getIntent()).isEqualTo("MEDICATION_SAFETY_BLOCK");
        assertThat(response.getMessage()).isEqualTo(PublicAssistantSafety.MEDICATION_BLOCK_MESSAGE);
        assertThat(response.getBookingDraft()).isNull();
        assertThat(response.getAvailableSlots()).isEmpty();
        assertThat(response.getSuggestedDoctor()).isNull();
        verifyNoInteractions(specialtyRepository, doctorProfileRepository, branchRepository, doctorWorkScheduleRepository, appointmentAvailabilityService);
    }

    @Test
    void diagnosisQuestionIsHardBlockedInsideAiBookingEndpoint() {
        PublicAssistantResponse response = service.chat(request("Tôi bị bệnh gì?", null));

        assertThat(response.getIntent()).isEqualTo("DIAGNOSIS_SAFETY_BLOCK");
        assertThat(response.getMessage()).isEqualTo(PublicAssistantSafety.DIAGNOSIS_BLOCK_MESSAGE);
        assertThat(response.getBookingDraft()).isNull();
        assertThat(response.getAvailableSlots()).isEmpty();
        assertThat(response.getSuggestedDoctor()).isNull();
    }

    @Test
    void systemQuestionForgotPasswordDoesNotRouteToBookingOrEye() {
        Optional<PublicAssistantResponse> response = service.tryHandle(request("Tôi quên mật khẩu", null));

        assertThat(response).isEmpty();
        verifyNoInteractions(specialtyRepository, doctorProfileRepository, branchRepository, doctorWorkScheduleRepository, appointmentAvailabilityService);
    }

    @Test
    void systemQuestionResultLookupDoesNotRouteToBookingOrDental() {
        Optional<PublicAssistantResponse> response = service.tryHandle(request("Trang tra cứu kết quả ở đâu?", null));

        assertThat(response).isEmpty();
        verifyNoInteractions(specialtyRepository, doctorProfileRepository, branchRepository, doctorWorkScheduleRepository, appointmentAvailabilityService);
    }

    @Test
    void systemQuestionMedicalRecordDoesNotRouteToBookingOrEnt() {
        Optional<PublicAssistantResponse> response = service.tryHandle(request("Tôi muốn xem hồ sơ", null));

        assertThat(response).isEmpty();
        verifyNoInteractions(specialtyRepository, doctorProfileRepository, branchRepository, doctorWorkScheduleRepository, appointmentAvailabilityService);
    }

    @Test
    void newDigestiveSymptomResetsOldPediatricsContext() {
        Specialty gastro = specialty(10L, "GASTRO", "Tiêu hóa");
        Specialty pediatrics = specialty(12L, "PED", "Nhi khoa");
        Specialty general = specialty(11L, "GENERAL", "Nội tổng quát");
        AiConversationContext oldContext = AiConversationContext.builder()
                .conversationId("conv-old-ped")
                .currentSpecialtyId(pediatrics.getId())
                .currentSpecialtyName("Nhi khoa")
                .build();

        PublicAssistantResponse response = chatWithMappedSpecialties(
                request("Tôi đau dạ dày", oldContext),
                gastro,
                gastro,
                pediatrics,
                general
        );

        assertThat(response.getContext().getCurrentSpecialtyId()).isEqualTo(gastro.getId());
        assertThat(response.getContext().getCurrentSpecialtyId()).isNotEqualTo(pediatrics.getId());
    }

    @Test
    void facilityFollowUpUsesFacilityContextAndDatabaseAddress() {
        Branch branch = branch();
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        AiConversationContext context = AiConversationContext.builder()
                .conversationId("conv-1")
                .currentFacilityId(BRANCH_ID)
                .currentFacilityName("PrimeCare Quận 1")
                .build();

        PublicAssistantRequest request = request("Cơ sở này ở đâu?", context);
        request.setHistory(List.of(historyMessage("assistant", "a".repeat(1500))));

        PublicAssistantResponse response = service.chat(request);

        assertThat(response.getIntent()).isEqualTo("FACILITY_INFO");
        assertThat(response.getMessage()).contains("12 Nguyễn Huệ");
        assertThat(response.getContext().getCurrentFacilityAddress()).isEqualTo("12 Nguyễn Huệ, Quận 1");
        assertThat(response.getSuggestedDoctor()).isNull();
        assertThat(response.getAvailableSlots()).isEmpty();
        assertThat(response.getActions()).isEmpty();
    }

    @Test
    void doctorSpecialtyFollowUpUsesDoctorContextWithoutReturningOldSlots() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        when(doctorProfileRepository.findPublicBookableById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        AiConversationContext context = AiConversationContext.builder()
                .conversationId("conv-doctor-info")
                .currentSpecialtyId(SPECIALTY_ID)
                .currentSpecialtyName("Tiêu hóa")
                .currentDoctorId(DOCTOR_ID)
                .currentDoctorName("Bác sĩ Nguyễn Văn A")
                .lastAvailableSlots(List.of(aiSlot("08:30")))
                .build();

        PublicAssistantResponse response = service.chat(request("Bác sĩ này chuyên gì?", context));

        assertThat(response.getIntent()).isEqualTo("DOCTOR_RECOMMENDATION");
        assertThat(response.getMessage()).contains("Tiêu hóa");
        assertThat(response.getAvailableSlots()).isEmpty();
        assertThat(response.getActions()).isEmpty();
    }

    @Test
    void viewMoreSlotsSkipsAlreadyShownSlotsAndReturnsNextSlots() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        stubSchedulesAndAvailability(doctor, gastro, true, List.of(
                slot(LocalTime.of(8, 30), true),
                slot(LocalTime.of(9, 0), true),
                slot(LocalTime.of(10, 30), true),
                slot(LocalTime.of(11, 0), true),
                slot(LocalTime.of(11, 30), true)
        ));
        PublicAssistantResponse first = service.chat(request("Khám tiêu hóa", null));

        PublicAssistantResponse more = service.chat(request("Còn giờ khác không?", first.getContext()));

        assertThat(more.getIntent()).isEqualTo("VIEW_MORE_SLOTS");
        assertThat(more.getAvailableSlots()).extracting(AiAvailableSlotResponse::getStartTime)
                .containsExactly("11:00", "11:30");
    }

    @Test
    void changeDoctorReturnsAnotherDoctorInSameSpecialty() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile firstDoctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        DoctorProfile otherDoctor = doctor(OTHER_DOCTOR_ID, "Bác sĩ Trần Thị B", gastro);
        stubSpecialties(gastro);
        when(specialtyRepository.findById(SPECIALTY_ID)).thenReturn(Optional.of(gastro));
        when(doctorProfileRepository.search(isNull(), eq(SPECIALTY_ID), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(firstDoctor, otherDoctor)));
        stubSchedulesAndAvailability(otherDoctor, gastro, true, List.of(
                slot(LocalTime.of(14, 0), true),
                slot(LocalTime.of(14, 30), true),
                slot(LocalTime.of(15, 0), true)
        ));
        AiConversationContext context = AiConversationContext.builder()
                .conversationId("conv-2")
                .currentSpecialtyId(SPECIALTY_ID)
                .currentSpecialtyName("Tiêu hóa")
                .currentDoctorId(DOCTOR_ID)
                .currentDoctorName("Bác sĩ Nguyễn Văn A")
                .build();

        PublicAssistantResponse response = service.chat(request("Bác sĩ khác thì sao?", context));

        assertThat(response.getIntent()).isEqualTo("CHANGE_DOCTOR");
        assertThat(response.getSuggestedDoctor().getDoctorId()).isEqualTo(OTHER_DOCTOR_ID);
        assertThat(response.getAvailableSlots()).extracting(AiAvailableSlotResponse::getStartTime)
                .containsExactly("14:00", "14:30", "15:00");
    }

    @Test
    void textSelectingNineOClockCreatesBookingDraftOnly() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        stubSchedulesAndAvailability(doctor, gastro, true, List.of(
                slot(LocalTime.of(8, 30), true),
                slot(LocalTime.of(9, 0), true),
                slot(LocalTime.of(10, 30), true)
        ));
        when(doctorProfileRepository.findPublicBookableById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(specialtyRepository.findById(SPECIALTY_ID)).thenReturn(Optional.of(gastro));
        when(appointmentAvailabilityService.getAvailability(BRANCH_ID, SPECIALTY_ID, DOCTOR_ID, VISIT_DATE, BranchSessionType.AM, false))
                .thenReturn(availability(doctor, gastro, List.of(
                        slot(LocalTime.of(8, 30), true),
                        slot(LocalTime.of(9, 0), true),
                        slot(LocalTime.of(10, 30), true)
                )));
        when(appointmentAvailabilityService.isSlotStillBookable(eq(VISIT_DATE), eq(LocalTime.of(9, 0)))).thenReturn(true);
        PublicAssistantResponse first = service.chat(request("Khám tiêu hóa", null));

        PublicAssistantRequest followUp = request("Tôi chọn 9h", first.getContext());
        followUp.setHistory(List.of(historyMessage("assistant", "a".repeat(1500))));

        PublicAssistantResponse selected = service.chat(followUp);

        assertThat(selected.getIntent()).isEqualTo("BOOKING_DRAFT_CREATED");
        assertThat(selected.getBookingDraft()).isNotNull();
        assertThat(selected.getBookingDraft().getStartTime()).isEqualTo("09:00");
        assertThat(selected.getBookingDraft().getSource()).isEqualTo("AI_ASSISTANT");
        assertThat(selected.getAvailableSlots()).isEmpty();
        assertThat(selected.getActions()).extracting(action -> action.getType()).containsExactly("GO_TO_BOOKING");
        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void selectingFirstSlotByTextCreatesBookingDraftOnly() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        stubSchedulesAndAvailability(doctor, gastro, true, List.of(
                slot(LocalTime.of(8, 30), true),
                slot(LocalTime.of(9, 0), true),
                slot(LocalTime.of(10, 30), true)
        ));
        when(doctorProfileRepository.findPublicBookableById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(specialtyRepository.findById(SPECIALTY_ID)).thenReturn(Optional.of(gastro));
        when(appointmentAvailabilityService.getAvailability(BRANCH_ID, SPECIALTY_ID, DOCTOR_ID, VISIT_DATE, BranchSessionType.AM, false))
                .thenReturn(availability(doctor, gastro, List.of(
                        slot(LocalTime.of(8, 30), true),
                        slot(LocalTime.of(9, 0), true),
                        slot(LocalTime.of(10, 30), true)
                )));
        when(appointmentAvailabilityService.isSlotStillBookable(eq(VISIT_DATE), eq(LocalTime.of(8, 30)))).thenReturn(true);
        PublicAssistantResponse first = service.chat(request("Khám tiêu hóa", null));

        PublicAssistantResponse selected = service.chat(request("Ca đầu", first.getContext()));

        assertThat(selected.getIntent()).isEqualTo("BOOKING_DRAFT_CREATED");
        assertThat(selected.getBookingDraft()).isNotNull();
        assertThat(selected.getBookingDraft().getStartTime()).isEqualTo("08:30");
        assertThat(selected.getAvailableSlots()).isEmpty();
        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void afternoonTimePreferenceReturnsAfternoonSlotsFromCurrentContext() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        when(doctorWorkScheduleRepository.findByDoctor_IdAndWorkDateBetweenOrderByWorkDateAscSessionAsc(
                eq(doctor.getId()),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenReturn(List.of(
                schedule(doctor, VISIT_DATE, BranchSessionType.AM),
                schedule(doctor, VISIT_DATE, BranchSessionType.PM)
        ));
        when(appointmentAvailabilityService.getAvailability(BRANCH_ID, SPECIALTY_ID, DOCTOR_ID, VISIT_DATE, BranchSessionType.AM, true))
                .thenReturn(availability(doctor, gastro, VISIT_DATE, BranchSessionType.AM, List.of(
                        slot(LocalTime.of(8, 30), true),
                        slot(LocalTime.of(9, 0), true)
                )));
        when(appointmentAvailabilityService.getAvailability(BRANCH_ID, SPECIALTY_ID, DOCTOR_ID, VISIT_DATE, BranchSessionType.PM, true))
                .thenReturn(availability(doctor, gastro, VISIT_DATE, BranchSessionType.PM, List.of(
                        slot(LocalTime.of(14, 0), true),
                        slot(LocalTime.of(14, 30), true),
                        slot(LocalTime.of(15, 0), true)
                )));
        PublicAssistantResponse first = service.chat(request("Khám tiêu hóa", null));

        PublicAssistantResponse afternoon = service.chat(request("Tôi muốn buổi chiều", first.getContext()));

        assertThat(afternoon.getIntent()).isEqualTo("TIME_PREFERENCE");
        assertThat(afternoon.getAvailableSlots()).extracting(AiAvailableSlotResponse::getStartTime)
                .containsExactly("14:00", "14:30", "15:00");
        assertThat(afternoon.getContext().getUserTimePreference()).isEqualTo("AFTERNOON");
    }

    @Test
    void tomorrowFollowUpSearchesFromTomorrowWithCurrentContext() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        stubSchedulesAndAvailability(doctor, gastro, true, defaultSlots());
        PublicAssistantResponse first = service.chat(request("Khám tiêu hóa", null));

        PublicAssistantResponse tomorrow = service.chat(request("Ngày mai có không?", first.getContext()));

        assertThat(tomorrow.getIntent()).isEqualTo("TIME_PREFERENCE");
        assertThat(tomorrow.getAvailableSlots()).isNotEmpty();
        assertThat(tomorrow.getAvailableSlots()).allSatisfy(slot ->
                assertThat(slot.getAppointmentDate()).isEqualTo(LocalDate.now().plusDays(1)));
    }

    @Test
    void afterTwoPmTomorrowCombinesDateAndTimePreference() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        when(doctorWorkScheduleRepository.findByDoctor_IdAndWorkDateBetweenOrderByWorkDateAscSessionAsc(
                eq(doctor.getId()),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenReturn(List.of(
                schedule(doctor, VISIT_DATE, BranchSessionType.AM),
                schedule(doctor, VISIT_DATE, BranchSessionType.PM)
        ));
        when(appointmentAvailabilityService.getAvailability(BRANCH_ID, SPECIALTY_ID, DOCTOR_ID, VISIT_DATE, BranchSessionType.AM, true))
                .thenReturn(availability(doctor, gastro, VISIT_DATE, BranchSessionType.AM, List.of(
                        slot(LocalTime.of(8, 30), true),
                        slot(LocalTime.of(9, 0), true)
                )));
        when(appointmentAvailabilityService.getAvailability(BRANCH_ID, SPECIALTY_ID, DOCTOR_ID, VISIT_DATE, BranchSessionType.PM, true))
                .thenReturn(availability(doctor, gastro, VISIT_DATE, BranchSessionType.PM, List.of(
                        slot(LocalTime.of(13, 30), true),
                        slot(LocalTime.of(14, 0), true),
                        slot(LocalTime.of(14, 30), true),
                        slot(LocalTime.of(15, 0), true)
                )));
        PublicAssistantResponse first = service.chat(request("Khám tiêu hóa", null));

        PublicAssistantResponse filtered = service.chat(request("Sau 14h ngày mai có không?", first.getContext()));

        assertThat(filtered.getIntent()).isEqualTo("TIME_PREFERENCE");
        assertThat(filtered.getAvailableSlots()).extracting(AiAvailableSlotResponse::getStartTime)
                .containsExactly("14:00", "14:30", "15:00");
        assertThat(filtered.getContext().getUserTimePreference()).isEqualTo("AFTER_14:00");
    }

    @Test
    void selectingUnavailableSlotReturnsFreshAlternativesInsteadOfDraft() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        stubSpecialties(gastro);
        stubDoctors(gastro, doctor);
        stubSchedulesAndAvailability(doctor, gastro, true, List.of(
                slot(LocalTime.of(8, 30), true),
                slot(LocalTime.of(9, 0), true),
                slot(LocalTime.of(10, 30), true),
                slot(LocalTime.of(11, 0), true)
        ));
        when(doctorProfileRepository.findPublicBookableById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(specialtyRepository.findById(SPECIALTY_ID)).thenReturn(Optional.of(gastro));
        when(appointmentAvailabilityService.getAvailability(BRANCH_ID, SPECIALTY_ID, DOCTOR_ID, VISIT_DATE, BranchSessionType.AM, false))
                .thenReturn(availability(doctor, gastro, List.of(
                        slot(LocalTime.of(9, 0), false)
                )));
        PublicAssistantResponse first = service.chat(request("Khám tiêu hóa", null));

        PublicAssistantResponse selected = service.chat(request("Tôi chọn 9h", first.getContext()));

        assertThat(selected.getIntent()).isEqualTo("SELECT_SLOT");
        assertThat(selected.getBookingDraft()).isNull();
        assertThat(selected.getMessage()).contains("Ca khám này hiện không còn khả dụng");
        assertThat(selected.getAvailableSlots()).isNotEmpty();
    }

    @Test
    void validateBookingDraftPayloadRejectsInvalidSlotId() {
        Optional<AiBookingDraftResponse> bookingDraft = service.validateBookingDraftPayload(Map.of(
                "slotId", "missing-slot"
        ));

        assertThat(bookingDraft).isEmpty();
        verifyNoInteractions(doctorProfileRepository, specialtyRepository, appointmentAvailabilityService);
    }

    @Test
    void validateBookingDraftPayloadRejectsUnavailableSlot() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        when(doctorProfileRepository.findPublicBookableById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(specialtyRepository.findById(SPECIALTY_ID)).thenReturn(Optional.of(gastro));
        when(appointmentAvailabilityService.getAvailability(BRANCH_ID, SPECIALTY_ID, DOCTOR_ID, VISIT_DATE, BranchSessionType.AM, false))
                .thenReturn(availability(doctor, gastro, List.of(slot(LocalTime.of(9, 0), false))));

        Optional<AiBookingDraftResponse> bookingDraft = service.validateBookingDraftPayload(Map.of(
                "slotId", aiSlot("09:00").getSlotId()
        ));

        assertThat(bookingDraft).isEmpty();
    }

    @Test
    void validateBookingDraftPayloadCreatesDraftOnlyForValidatedSlot() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Nguyễn Văn A", gastro);
        when(doctorProfileRepository.findPublicBookableById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(specialtyRepository.findById(SPECIALTY_ID)).thenReturn(Optional.of(gastro));
        when(appointmentAvailabilityService.getAvailability(BRANCH_ID, SPECIALTY_ID, DOCTOR_ID, VISIT_DATE, BranchSessionType.AM, false))
                .thenReturn(availability(doctor, gastro, List.of(slot(LocalTime.of(9, 0), true))));
        when(appointmentAvailabilityService.isSlotStillBookable(VISIT_DATE, LocalTime.of(9, 0))).thenReturn(true);

        Optional<AiBookingDraftResponse> bookingDraft = service.validateBookingDraftPayload(Map.of(
                "slotId", aiSlot("09:00").getSlotId()
        ));

        assertThat(bookingDraft).isPresent();
        assertThat(bookingDraft.get().getSlotId()).isEqualTo(aiSlot("09:00").getSlotId());
        assertThat(bookingDraft.get().getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(bookingDraft.get().getSource()).isEqualTo("AI_ASSISTANT");
    }

    @Test
    void gastroExpertiseCanOutrankSlightlyEarlierSlotForStomachPain() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile earlyDoctor = doctor(DOCTOR_ID, "Bác sĩ Có Lịch Sớm", gastro);
        DoctorProfile expertDoctor = doctor(OTHER_DOCTOR_ID, "Bác sĩ Dạ Dày", gastro);
        expertDoctor.setExpertiseVn("Dạ dày, trào ngược, đau bụng, rối loạn tiêu hóa");
        stubSpecialties(gastro);
        when(doctorProfileRepository.search(isNull(), eq(SPECIALTY_ID), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(earlyDoctor, expertDoctor)));
        stubScheduleAndAvailabilityOnDate(earlyDoctor, gastro, VISIT_DATE, BranchSessionType.AM, List.of(slot(LocalTime.of(8, 30), true)));
        stubScheduleAndAvailabilityOnDate(expertDoctor, gastro, VISIT_DATE.plusDays(1), BranchSessionType.AM, List.of(slot(LocalTime.of(9, 0), true)));

        PublicAssistantResponse response = service.chat(request("Tôi đau dạ dày", null));

        assertThat(response.getSuggestedDoctor().getDoctorId()).isEqualTo(OTHER_DOCTOR_ID);
        assertThat(response.getMessage()).contains("chuyên môn liên quan đến vấn đề dạ dày/tiêu hóa");
    }

    @Test
    void earliestSlotWinsWhenDoctorsDoNotHaveSymptomExpertiseMatch() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile earlyDoctor = doctor(DOCTOR_ID, "Bác sĩ Có Lịch Sớm", gastro);
        DoctorProfile laterDoctor = doctor(OTHER_DOCTOR_ID, "Bác sĩ Lịch Trễ", gastro);
        stubSpecialties(gastro);
        when(doctorProfileRepository.search(isNull(), eq(SPECIALTY_ID), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(earlyDoctor, laterDoctor)));
        stubScheduleAndAvailabilityOnDate(earlyDoctor, gastro, VISIT_DATE, BranchSessionType.AM, List.of(slot(LocalTime.of(8, 30), true)));
        stubScheduleAndAvailabilityOnDate(laterDoctor, gastro, VISIT_DATE.plusDays(1), BranchSessionType.AM, List.of(slot(LocalTime.of(9, 0), true)));

        PublicAssistantResponse response = service.chat(request("Tôi đau dạ dày", null));

        assertThat(response.getSuggestedDoctor().getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(response.getMessage()).contains("phù hợp chuyên khoa");
    }

    @Test
    void matchingDoctorWithoutSlotsIsReturnedWithoutSelectSlotActions() {
        Specialty gastro = specialty(SPECIALTY_ID, "GASTRO", "Tiêu hóa");
        DoctorProfile doctor = doctor(DOCTOR_ID, "Bác sĩ Dạ Dày", gastro);
        doctor.setExpertiseVn("Dạ dày, trào ngược, đau bụng, tiêu hóa");
        stubSpecialties(gastro);
        when(doctorProfileRepository.search(isNull(), eq(SPECIALTY_ID), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor)));
        when(doctorWorkScheduleRepository.findByDoctor_IdAndWorkDateBetweenOrderByWorkDateAscSessionAsc(
                eq(doctor.getId()),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenReturn(List.of());

        PublicAssistantResponse response = service.chat(request("Tôi đau dạ dày", null));

        assertThat(response.getSuggestedDoctor().getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(response.getAvailableSlots()).isEmpty();
        assertThat(response.getMessage()).contains("hiện chưa có lịch trống phù hợp");
        assertThat(response.getActions()).extracting(action -> action.getType()).doesNotContain("SELECT_SLOT");
    }

    @Test
    void aiBookingAssistantHasNoAppointmentCreationRepositoryDependency() {
        boolean hasAppointmentRepositoryDependency = Arrays.stream(AiBookingAssistantService.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(AppointmentRepository.class::equals);

        assertThat(hasAppointmentRepositoryDependency).isFalse();
    }

    private PublicAssistantRequest request(String question, AiConversationContext context) {
        PublicAssistantRequest request = new PublicAssistantRequest();
        request.setQuestion(question);
        request.setContext(context);
        request.setConversationId(context != null ? context.getConversationId() : null);
        return request;
    }

    private PublicAssistantResponse chatWithMappedSpecialties(String question, Specialty expectedSpecialty, Specialty... specialties) {
        return chatWithMappedSpecialties(request(question, null), expectedSpecialty, specialties);
    }

    private PublicAssistantResponse chatWithMappedSpecialties(PublicAssistantRequest request,
                                                             Specialty expectedSpecialty,
                                                             Specialty... specialties) {
        stubSpecialties(specialties);
        Map<Long, DoctorProfile> doctorsBySpecialty = new LinkedHashMap<>();
        for (Specialty specialty : specialties) {
            DoctorProfile doctor = doctor(1000L + specialty.getId(), "Bác sĩ " + specialty.getCode(), specialty);
            doctorsBySpecialty.put(specialty.getId(), doctor);
            stubSchedulesAndAvailability(doctor, specialty, true, defaultSlots());
        }
        when(doctorProfileRepository.search(isNull(), any(), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Long specialtyId = invocation.getArgument(1);
                    DoctorProfile doctor = doctorsBySpecialty.get(specialtyId);
                    return new PageImpl<>(doctor == null ? List.of() : List.of(doctor));
                });

        PublicAssistantResponse response = service.chat(request);

        assertThat(response.getContext().getCurrentSpecialtyId()).as("resolved specialty id").isNotNull();
        assertThat(doctorsBySpecialty).containsKey(expectedSpecialty.getId());
        return response;
    }

    private List<BookableSlotResponse> defaultSlots() {
        return List.of(
                slot(LocalTime.of(8, 30), true),
                slot(LocalTime.of(9, 0), true),
                slot(LocalTime.of(10, 30), true)
        );
    }

    private AiAvailableSlotResponse aiSlot(String startTime) {
        return AiAvailableSlotResponse.builder()
                .slotId("SLOT|" + BRANCH_ID + "|" + SPECIALTY_ID + "|" + DOCTOR_ID + "|" + VISIT_DATE + "|AM|" + startTime)
                .doctorId(DOCTOR_ID)
                .doctorName("Bác sĩ Nguyễn Văn A")
                .specialtyId(SPECIALTY_ID)
                .specialtyName("Tiêu hóa")
                .facilityId(BRANCH_ID)
                .facilityName("PrimeCare Quận 1")
                .facilityAddress("12 Nguyễn Huệ, Quận 1")
                .appointmentDate(VISIT_DATE)
                .session("AM")
                .startTime(startTime)
                .endTime("09:00")
                .displayLabel(startTime)
                .build();
    }

    private PublicAssistantMessageRequest historyMessage(String role, String text) {
        PublicAssistantMessageRequest message = new PublicAssistantMessageRequest();
        message.setRole(role);
        message.setText(text);
        return message;
    }

    private void stubSpecialties(Specialty... specialties) {
        when(specialtyRepository.findAllByStatus(eq("ACTIVE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(specialties)));
        for (Specialty specialty : specialties) {
            when(specialtyRepository.findById(specialty.getId())).thenReturn(Optional.of(specialty));
        }
    }

    private void stubDoctors(Specialty specialty, DoctorProfile... doctors) {
        when(doctorProfileRepository.search(isNull(), eq(specialty.getId()), isNull(), eq(DoctorStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctors)));
        for (DoctorProfile doctor : doctors) {
            when(doctorProfileRepository.findPublicBookableById(doctor.getId())).thenReturn(Optional.of(doctor));
        }
    }

    private void stubSchedulesAndAvailability(DoctorProfile doctor,
                                              Specialty specialty,
                                              boolean onlyAvailable,
                                              List<BookableSlotResponse> slots) {
        when(doctorWorkScheduleRepository.findByDoctor_IdAndWorkDateBetweenOrderByWorkDateAscSessionAsc(
                eq(doctor.getId()),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenReturn(List.of(schedule(doctor, VISIT_DATE, BranchSessionType.AM)));
        when(appointmentAvailabilityService.getAvailability(
                doctor.getBranch().getId(),
                specialty.getId(),
                doctor.getId(),
                VISIT_DATE,
                BranchSessionType.AM,
                onlyAvailable
        )).thenReturn(availability(doctor, specialty, slots));
    }

    private void stubScheduleAndAvailabilityOnDate(DoctorProfile doctor,
                                                   Specialty specialty,
                                                   LocalDate visitDate,
                                                   BranchSessionType session,
                                                   List<BookableSlotResponse> slots) {
        when(doctorWorkScheduleRepository.findByDoctor_IdAndWorkDateBetweenOrderByWorkDateAscSessionAsc(
                eq(doctor.getId()),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenReturn(List.of(schedule(doctor, visitDate, session)));
        when(appointmentAvailabilityService.getAvailability(
                doctor.getBranch().getId(),
                specialty.getId(),
                doctor.getId(),
                visitDate,
                session,
                true
        )).thenReturn(availability(doctor, specialty, visitDate, session, slots));
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
                                                         List<BookableSlotResponse> slots) {
        return availability(doctor, specialty, VISIT_DATE, BranchSessionType.AM, slots);
    }

    private AppointmentAvailabilityResponse availability(DoctorProfile doctor,
                                                         Specialty specialty,
                                                         LocalDate visitDate,
                                                         BranchSessionType session,
                                                         List<BookableSlotResponse> slots) {
        return AppointmentAvailabilityResponse.builder()
                .branchId(doctor.getBranch().getId())
                .branchNameVn(doctor.getBranch().getNameVn())
                .branchNameEn(doctor.getBranch().getNameEn())
                .specialtyId(specialty.getId())
                .specialtyNameVn(specialty.getNameVn())
                .doctorId(doctor.getId())
                .doctorName(doctor.getFullName())
                .visitDate(visitDate)
                .session(session)
                .slotMinutes(30)
                .totalSlots(slots.size())
                .remainingSlots((int) slots.stream().filter(BookableSlotResponse::isAvailable).count())
                .slots(slots)
                .build();
    }

    private BookableSlotResponse slot(LocalTime start, boolean available) {
        return BookableSlotResponse.builder()
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .available(available)
                .capacity(1)
                .bookedCount(available ? 0 : 1)
                .remainingSlots(available ? 1 : 0)
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
