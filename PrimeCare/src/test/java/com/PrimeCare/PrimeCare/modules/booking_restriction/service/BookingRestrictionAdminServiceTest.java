package com.PrimeCare.PrimeCare.modules.booking_restriction.service;

import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.dto.request.CreateBookingRestrictionOverrideRequest;
import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.BookingRestrictionOverride;
import com.PrimeCare.PrimeCare.modules.booking_restriction.entity.PatientBookingRestriction;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionLevel;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionOverrideStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.enums.BookingRestrictionStatus;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.BookingRestrictionOverrideRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.PatientBookingRestrictionRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.repository.PatientViolationEventRepository;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientRepository;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingRestrictionAdminServiceTest {

    @Mock
    private PatientBookingRestrictionRepository restrictionRepository;
    @Mock
    private PatientViolationEventRepository violationEventRepository;
    @Mock
    private BookingRestrictionOverrideRepository overrideRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BookingIdentityService bookingIdentityService;
    @Mock
    private BookingRestrictionPolicyService bookingRestrictionPolicyService;
    @Mock
    private PatientViolationEventService patientViolationEventService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private BookingRestrictionAdminService service;

    @Test
    void listIncludesStaffActionCapabilities() {
        PatientBookingRestriction restriction = PatientBookingRestriction.builder()
                .id(1L)
                .identityKeyHash("hash")
                .periodMonth("2026-05")
                .level(BookingRestrictionLevel.VERIFY_REQUIRED)
                .status(BookingRestrictionStatus.ACTIVE)
                .scoreSnapshot(5)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(2))
                .build();

        when(restrictionRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(restriction)));
        when(bookingRestrictionPolicyService.monthlyScore("hash", "2026-05")).thenReturn(5);

        var response = service.list(
                BookingRestrictionStatus.ACTIVE,
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );
        var summary = response.getItems().get(0);

        assertThat(summary.getCanLift()).isTrue();
        assertThat(summary.getCanOverrideOnce()).isTrue();
        assertThat(summary.getCanStaffPardon()).isTrue();
        assertThat(summary.getCanCreateManualViolation()).isTrue();
        assertThat(summary.getSupportedActions()).containsExactly(
                "LIFT",
                "OVERRIDE_ONCE",
                "STAFF_PARDON",
                "CREATE_MANUAL_VIOLATION"
        );
    }

    @Test
    void overrideOnceRequiresReason() {
        CreateBookingRestrictionOverrideRequest request = new CreateBookingRestrictionOverrideRequest();
        request.setReason(" ");

        when(userRepository.findById(10L)).thenReturn(Optional.of(User.builder().id(10L).build()));

        assertThatThrownBy(() -> service.overrideOnce(1L, request, 10L))
                .isInstanceOf(ApiException.class);

        verify(overrideRepository, never()).save(any());
    }

    @Test
    void overrideOnceCreatesActiveOverrideWithDefaultTwentyFourHours() {
        User staff = User.builder().id(10L).build();
        PatientBookingRestriction restriction = PatientBookingRestriction.builder()
                .id(1L)
                .identityKeyHash("hash")
                .periodMonth("2026-05")
                .level(BookingRestrictionLevel.VERIFY_REQUIRED)
                .status(BookingRestrictionStatus.ACTIVE)
                .scoreSnapshot(5)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(2))
                .build();
        CreateBookingRestrictionOverrideRequest request = new CreateBookingRestrictionOverrideRequest();
        request.setReason("Cho đặt một lần sau khi gọi xác nhận");

        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(restrictionRepository.findById(1L)).thenReturn(Optional.of(restriction));
        when(overrideRepository.save(any(BookingRestrictionOverride.class))).thenAnswer(invocation -> {
            BookingRestrictionOverride override = invocation.getArgument(0);
            override.setId(99L);
            return override;
        });

        var response = service.overrideOnce(1L, request, 10L);

        assertThat(response.getId()).isEqualTo(99L);
        BookingRestrictionOverride saved = savedOverride();
        assertThat(saved.getStatus()).isEqualTo(BookingRestrictionOverrideStatus.ACTIVE);
        assertThat(saved.getIdentityKeyHash()).isEqualTo("hash");
        assertThat(saved.getRestriction()).isSameAs(restriction);
        assertThat(saved.getCreatedBy()).isSameAs(staff);
        assertThat(saved.getReason()).isEqualTo("Cho đặt một lần sau khi gọi xác nhận");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
        assertThat(saved.getExpiresAt()).isBefore(LocalDateTime.now().plusHours(25));
    }

    private BookingRestrictionOverride savedOverride() {
        ArgumentCaptor<BookingRestrictionOverride> captor = ArgumentCaptor.forClass(BookingRestrictionOverride.class);
        verify(overrideRepository).save(captor.capture());
        return captor.getValue();
    }
}
