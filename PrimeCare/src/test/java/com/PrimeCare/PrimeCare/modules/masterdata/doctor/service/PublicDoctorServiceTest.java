package com.PrimeCare.PrimeCare.modules.masterdata.doctor.service;

import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentAvailabilityService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorSpecialty;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorSpecialtyId;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.service.BranchSpecialtyService;
import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicDoctorServiceTest {

    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BranchSpecialtyService branchSpecialtyService;
    @Mock
    private DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    @Mock
    private AppointmentAvailabilityService appointmentAvailabilityService;

    @InjectMocks
    private PublicDoctorService service;

    @Test
    void searchReturnsBookableDoctorsFromPublicRepositoryQuery() {
        DoctorProfile doctor = doctor(3L);
        Pageable pageable = PageRequest.of(0, 12);
        when(doctorProfileRepository.search(null, null, null, DoctorStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(doctor), pageable, 1));
        when(userRepository.findByDoctorProfile_Id(3L)).thenReturn(Optional.of(account(UserStatus.ACTIVE)));

        var response = service.search(null, null, null, pageable);

        assertThat(response.getItems()).hasSize(1);
        var item = response.getItems().getFirst();
        assertThat(item.getId()).isEqualTo(3L);
        assertThat(item.isBookable()).isTrue();
        assertThat(item.isOperationalReady()).isTrue();
        assertThat(item.isHasAccount()).isTrue();
        assertThat(item.getAccountId()).isNull();
        assertThat(item.getAccountEmail()).isNull();
        assertThat(item.getAccountStatus()).isNull();
        verify(doctorProfileRepository).search(null, null, null, DoctorStatus.ACTIVE, pageable);
    }

    @Test
    void searchDefensivelyReturnsSafeNonBookableReasonWithoutAccountDetails() {
        DoctorProfile doctor = doctor(4L);
        Pageable pageable = PageRequest.of(0, 12);
        when(doctorProfileRepository.search(null, null, null, DoctorStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(doctor), pageable, 1));
        when(userRepository.findByDoctorProfile_Id(4L)).thenReturn(Optional.empty());

        var response = service.search(null, null, null, pageable);

        var item = response.getItems().getFirst();
        assertThat(item.isBookable()).isFalse();
        assertThat(item.getNotReadyReason()).isEqualTo(DoctorOperationalGuardService.PUBLIC_REASON_NO_ACTIVE_DOCTOR_ACCOUNT);
        assertThat(item.isHasAccount()).isFalse();
        assertThat(item.getAccountId()).isNull();
        assertThat(item.getAccountEmail()).isNull();
        assertThat(item.getAccountStatus()).isNull();
    }

    @Test
    void searchIncludesOnlySpecialtiesThatAreValidForPublicBooking() {
        DoctorProfile doctor = doctorWithoutSpecialties(5L);
        addSpecialty(doctor, specialty(20L, "CARD", "Tim mạch", "Cardiology", "ACTIVE"));
        addSpecialty(doctor, specialty(21L, "DERM", "Da liễu", "Dermatology", "INACTIVE"));
        addSpecialty(doctor, specialty(22L, "ENT", "Tai mũi họng", "ENT", "ACTIVE"));
        Pageable pageable = PageRequest.of(0, 12);
        when(doctorProfileRepository.search(null, null, null, DoctorStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(doctor), pageable, 1));
        when(userRepository.findByDoctorProfile_Id(5L)).thenReturn(Optional.of(account(UserStatus.ACTIVE)));
        lenient().doThrow(new ApiException(ErrorCode.INVALID_REQUEST, "branch specialty inactive"))
                .when(branchSpecialtyService).validateBranchSpecialtyActive(1L, 22L);

        var response = service.search(null, null, null, pageable);

        var item = response.getItems().getFirst();
        assertThat(item.getSpecialtyIds()).containsExactly(20L);
        assertThat(item.getSpecialtyNameVn()).isEqualTo("Tim mạch");
        assertThat(item.getSpecialtyNameEn()).isEqualTo("Cardiology");
    }

    @Test
    void publicDoctorWithNoPublicValidSpecialtyIsNotBookable() {
        DoctorProfile doctor = doctorWithoutSpecialties(6L);
        addSpecialty(doctor, specialty(30L, "ENT", "Tai mũi họng", "ENT", "ACTIVE"));
        Pageable pageable = PageRequest.of(0, 12);
        when(doctorProfileRepository.search(null, null, null, DoctorStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(doctor), pageable, 1));
        when(userRepository.findByDoctorProfile_Id(6L)).thenReturn(Optional.of(account(UserStatus.ACTIVE)));
        doThrow(new ApiException(ErrorCode.INVALID_REQUEST, "branch specialty inactive"))
                .when(branchSpecialtyService).validateBranchSpecialtyActive(1L, 30L);

        var response = service.search(null, null, null, pageable);

        var item = response.getItems().getFirst();
        assertThat(item.getSpecialtyIds()).isEmpty();
        assertThat(item.isHasAccount()).isTrue();
        assertThat(item.isBookable()).isFalse();
        assertThat(item.isOperationalReady()).isFalse();
        assertThat(item.getNotReadyReason()).isEqualTo(DoctorOperationalGuardService.PUBLIC_REASON_NOT_AVAILABLE);
    }

    @Test
    void getByIdRejectsDoctorExcludedByBookableQuery() {
        when(doctorProfileRepository.findPublicBookableById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(3L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DOCTOR_NOT_FOUND)
                );
    }

    private DoctorProfile doctor(Long id) {
        DoctorProfile doctor = doctorWithoutSpecialties(id);
        addSpecialty(doctor, specialty(11L, "GEN", "Tổng quát", "General", "ACTIVE"));
        return doctor;
    }

    private DoctorProfile doctorWithoutSpecialties(Long id) {
        return DoctorProfile.builder()
                .id(id)
                .fullName("Dr Test")
                .branch(Branch.builder()
                        .id(1L)
                        .nameVn("PrimeCare")
                        .status(BranchStatus.ACTIVE)
                        .build())
                .status(DoctorStatus.ACTIVE)
                .doctorSpecialties(new java.util.HashSet<>())
                .build();
    }

    private void addSpecialty(DoctorProfile doctor, Specialty specialty) {
        DoctorSpecialty doctorSpecialty = DoctorSpecialty.builder()
                .id(new DoctorSpecialtyId(doctor.getId(), specialty.getId()))
                .doctor(doctor)
                .specialty(specialty)
                .build();
        doctor.getDoctorSpecialties().add(doctorSpecialty);
    }

    private Specialty specialty(Long id, String code, String nameVn, String nameEn, String status) {
        return Specialty.builder()
                .id(id)
                .code(code)
                .nameVn(nameVn)
                .nameEn(nameEn)
                .status(status)
                .build();
    }

    private User account(UserStatus status) {
        return User.builder()
                .id(10L)
                .role(UserRole.DOCTOR)
                .status(status)
                .build();
    }
}
