package com.PrimeCare.PrimeCare.modules.encounter.service;

import com.PrimeCare.PrimeCare.modules.ai.support.OpenAiCompatibleClient;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncounterAiSummaryServiceTest {

    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ServiceOrderRepository serviceOrderRepository;
    @Mock
    private ServiceResultRepository serviceResultRepository;
    @Mock
    private OpenAiCompatibleClient openAiCompatibleClient;

    private EncounterAiSummaryService service;

    @BeforeEach
    void setUp() {
        service = new EncounterAiSummaryService(
                encounterRepository,
                userRepository,
                serviceOrderRepository,
                serviceResultRepository,
                new ObjectMapper(),
                openAiCompatibleClient
        );
    }

    @Test
    void shouldNotSendPatientPiiInAiPromptWhenGeneratingSummary() {
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").build();
        User doctorUser = User.builder().id(9L).doctorProfile(doctor).build();
        Encounter encounter = Encounter.builder()
                .id(1L)
                .doctor(doctor)
                .patientFullNameSnapshot("Nguyen Van A")
                .patientPhoneSnapshot("0901234567")
                .patientEmailSnapshot("patient.nguyen@example.test")
                .patientDobSnapshot(LocalDate.of(1990, 5, 20))
                .patientGenderSnapshot(Gender.MALE)
                .status(EncounterStatus.IN_PROGRESS)
                .chiefComplaint("Ho sốt của Nguyen Van A")
                .clinicalNote("Liên hệ patient.nguyen@example.test hoặc 0901234567 khi cần.")
                .preliminaryDiagnosis("Viêm họng cấp")
                .build();
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        when(userRepository.findById(9L)).thenReturn(Optional.of(doctorUser));
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(serviceOrderRepository.findByEncounter_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(serviceResultRepository.findByServiceOrderItem_ServiceOrder_Encounter_Id(1L)).thenReturn(List.of());
        when(openAiCompatibleClient.generateText(anyString(), promptCaptor.capture())).thenReturn(Optional.of("AI draft"));
        when(openAiCompatibleClient.providerLabel()).thenReturn("TEST_AI");

        var response = service.generate(1L, 9L);

        String prompt = promptCaptor.getValue();
        assertThat(response.getProvider()).isEqualTo("TEST_AI");
        assertThat(prompt)
                .doesNotContain("Nguyen Van A")
                .doesNotContain("patient.nguyen@example.test")
                .doesNotContain("0901234567")
                .doesNotContain("patientName")
                .contains("birthYear")
                .contains("1990")
                .contains("MALE");
    }
}
