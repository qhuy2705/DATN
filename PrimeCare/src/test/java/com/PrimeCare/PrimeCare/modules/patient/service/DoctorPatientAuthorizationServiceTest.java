package com.PrimeCare.PrimeCare.modules.patient.service;

import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientAllergyRepository;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientRepository;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorPatientAuthorizationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientAllergyRepository patientAllergyRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private EncounterRepository encounterRepository;

    private DoctorPatientAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new DoctorPatientAuthorizationService(
                userRepository,
                patientRepository,
                patientAllergyRepository,
                appointmentRepository,
                encounterRepository
        );
    }

    @Test
    void requireAccessAllowsDoctorWithAccessibleAppointment() {
        Long patientId = 10L;
        Long doctorProfileId = 20L;
        givenDoctorUser(99L, doctorProfileId);
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(appointmentRepository.existsByDoctor_IdAndPatient_IdAndStatusIn(eq(doctorProfileId), eq(patientId), any()))
                .thenReturn(true);

        assertThatCode(() -> service.requireAccess(patientId, 99L)).doesNotThrowAnyException();

        verify(encounterRepository, never()).existsByDoctor_IdAndPatient_IdAndStatusIn(any(), any(), any());
    }

    @Test
    void requireAccessAllowsEncounterFallbackWhenNoAppointmentAccess() {
        Long patientId = 10L;
        Long doctorProfileId = 20L;
        givenDoctorUser(99L, doctorProfileId);
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(appointmentRepository.existsByDoctor_IdAndPatient_IdAndStatusIn(eq(doctorProfileId), eq(patientId), any()))
                .thenReturn(false);
        when(encounterRepository.existsByDoctor_IdAndPatient_IdAndStatusIn(eq(doctorProfileId), eq(patientId), any()))
                .thenReturn(true);

        assertThatCode(() -> service.requireAccess(patientId, 99L)).doesNotThrowAnyException();
    }

    @Test
    void requireAccessDeniesDoctorWithoutAppointmentOrEncounterAccess() {
        Long patientId = 10L;
        Long doctorProfileId = 20L;
        givenDoctorUser(99L, doctorProfileId);
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(appointmentRepository.existsByDoctor_IdAndPatient_IdAndStatusIn(eq(doctorProfileId), eq(patientId), any()))
                .thenReturn(false);
        when(encounterRepository.existsByDoctor_IdAndPatient_IdAndStatusIn(eq(doctorProfileId), eq(patientId), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.requireAccess(patientId, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED)
                );
    }

    @Test
    void requireAccessFailsBeforeDoctorLookupWhenPatientDoesNotExist() {
        when(patientRepository.existsById(10L)).thenReturn(false);

        assertThatThrownBy(() -> service.requireAccess(10L, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PATIENT_NOT_FOUND)
                );

        verify(userRepository, never()).findById(any());
    }

    @Test
    void requireAccessRejectsUserWithoutDoctorProfile() {
        when(patientRepository.existsById(10L)).thenReturn(true);
        when(userRepository.findById(99L)).thenReturn(Optional.of(User.builder().id(99L).build()));

        assertThatThrownBy(() -> service.requireAccess(10L, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED)
                );
    }

    private void givenDoctorUser(Long userId, Long doctorProfileId) {
        User user = User.builder()
                        .id(userId)
                        .doctorProfile(DoctorProfile.builder().id(doctorProfileId).fullName("Dr Test").build())
                        .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }
}
