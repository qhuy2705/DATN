package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.request.CreateAppointmentRequest;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.service.BookingEmailOtpService;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.BookingIdentityService;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.BookingRestrictionPolicyService;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchSessionRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.DoctorOperationalGuardService;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.BranchSpecialtyService;
import com.PrimeCare.PrimeCare.modules.patient.service.PatientService;
import com.PrimeCare.PrimeCare.modules.triage.PreTriageService;
import com.PrimeCare.PrimeCare.modules.triage.TriageAuditService;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceDoctorReadinessTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentAvailabilityService availabilityService;
    @Mock
    private AppointmentSlotAvailabilityGuard slotAvailabilityGuard;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private BranchSpecialtyService branchSpecialtyService;
    @Mock
    private DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    @Mock
    private BranchSessionRepository branchSessionRepository;
    @Mock
    private AppointmentCodeGenerator appointmentCodeGenerator;
    @Mock
    private PatientService patientService;
    @Mock
    private DoctorOperationalGuardService doctorOperationalGuardService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private PreTriageService preTriageService;
    @Mock
    private TriageAuditService triageAuditService;
    @Mock
    private BookingIdentityService bookingIdentityService;
    @Mock
    private BookingRestrictionPolicyService bookingRestrictionPolicyService;
    @Mock
    private BookingEmailOtpService bookingEmailOtpService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppointmentService service;

    @Test
    void createRejectsNonBookableDoctorWithPublicSafeMessage() {
        Branch branch = Branch.builder().id(1L).nameVn("PrimeCare").build();
        Specialty specialty = Specialty.builder().id(2L).nameVn("Noi tong quat").build();
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").branch(branch).build();
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(specialtyRepository.findById(2L)).thenReturn(Optional.of(specialty));
        when(doctorProfileRepository.findByIdAndBranch_Id(3L, 1L)).thenReturn(Optional.of(doctor));
        doThrow(new ApiException(
                ErrorCode.INVALID_REQUEST,
                DoctorOperationalGuardService.PUBLIC_DOCTOR_NOT_AVAILABLE_MESSAGE
        )).when(doctorOperationalGuardService).assertDoctorPublicBookable(doctor);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo(DoctorOperationalGuardService.PUBLIC_DOCTOR_NOT_AVAILABLE_MESSAGE);
                });

        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    private CreateAppointmentRequest request() {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setBranchId(1L);
        request.setSpecialtyId(2L);
        request.setDoctorId(3L);
        request.setVisitDate(LocalDate.now().plusDays(1));
        request.setSession(BranchSessionType.AM);
        request.setSlotStart(LocalTime.of(8, 0));
        request.setPatientFullName("Nguyen Van A");
        request.setPatientPhone("0900000000");
        request.setPatientEmail("patient@example.com");
        request.setPatientDob(LocalDate.of(1990, 1, 1));
        request.setPatientGender(Gender.MALE);
        request.setVisitType("new");
        return request;
    }
}
