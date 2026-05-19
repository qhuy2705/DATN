package com.PrimeCare.PrimeCare.modules.patient.service;

import com.PrimeCare.PrimeCare.modules.patient.dto.request.UpdatePatientRequest;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientAllergyRepository;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientRepository;
import com.PrimeCare.PrimeCare.shared.enums.PatientStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientAllergyRepository patientAllergyRepository;
    @Mock
    private DoctorPatientAuthorizationService doctorPatientAuthorizationService;

    @InjectMocks
    private PatientService service;

    @Test
    void shouldKeepExistingEmailWhenUpdateRequestDoesNotSendEmail() {
        Patient patient = Patient.builder()
                .id(1L)
                .code("PT001")
                .fullName("Nguyen Van A")
                .phone("0900000000")
                .email("old@example.test")
                .status(PatientStatus.ACTIVE)
                .build();
        UpdatePatientRequest request = new UpdatePatientRequest();
        request.setFullName("Nguyen Van B");

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(patientRepository.save(patient)).thenReturn(patient);
        when(patientAllergyRepository.findByPatient_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        var response = service.update(1L, request);

        assertThat(response.getFullName()).isEqualTo("Nguyen Van B");
        assertThat(response.getEmail()).isEqualTo("old@example.test");
        assertThat(patient.getEmail()).isEqualTo("old@example.test");
    }
}
