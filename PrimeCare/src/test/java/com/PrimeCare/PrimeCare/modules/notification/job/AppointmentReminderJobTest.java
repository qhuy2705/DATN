package com.PrimeCare.PrimeCare.modules.notification.job;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.notification.entity.NotificationPreference;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentReminderMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.repository.NotificationPreferenceRepository;
import com.PrimeCare.PrimeCare.modules.notification.repository.NotificationRepository;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentReminderJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 13, 8, 0);

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AppointmentMailEventPublisher appointmentMailEventPublisher;
    @Mock
    private InternalNotificationService internalNotificationService;
    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;
    @Mock
    private UserRepository userRepository;

    private AppointmentReminderJob job;

    @BeforeEach
    void setUp() {
        job = new AppointmentReminderJob(
                appointmentRepository,
                notificationRepository,
                appointmentMailEventPublisher,
                internalNotificationService,
                notificationPreferenceRepository,
                userRepository
        );
        ReflectionTestUtils.setField(job, "scanWindowMinutes", 20L);
    }

    @Test
    void confirmedAppointmentIn24hWindowWithEnoughLeadPublishes24hReminder() {
        Appointment appointment = appointmentAt(NOW.plusHours(24).plusMinutes(5), AppointmentStatus.CONFIRMED);
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW.minusHours(6));
        whenCandidates(appointment);

        job.dispatchReminders(NOW);

        AppointmentReminderMailEvent event = singlePublishedEvent();
        assertThat(event.templateCode()).isEqualTo(AppointmentReminderJob.REMINDER_24H_TEMPLATE);
        assertThat(event.reminderKind()).isEqualTo("24H");
        assertThat(event.cancellationDeadline()).isEqualTo("14/05/2026 04:05");
    }

    @Test
    void confirmedAppointmentIn6hWindowWithEnoughLeadPublishes6hReminder() {
        Appointment appointment = appointmentAt(NOW.plusHours(6).plusMinutes(5), AppointmentStatus.CONFIRMED);
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW.minusHours(3));
        whenCandidates(appointment);
        lenient().when(notificationRepository.existsByAppointment_IdAndTemplateCodeAndStatusIn(
                eq(appointment.getId()),
                eq(AppointmentReminderJob.REMINDER_24H_TEMPLATE),
                any()
        )).thenReturn(true);

        job.dispatchReminders(NOW);

        AppointmentReminderMailEvent event = singlePublishedEvent();
        assertThat(event.templateCode()).isEqualTo(AppointmentReminderJob.REMINDER_6H_TEMPLATE);
        assertThat(event.reminderKind()).isEqualTo("6H");
    }

    @Test
    void appointmentConfirmedOnly24hBeforeVisitDoesNotPublish24hReminder() {
        Appointment appointment = appointmentAt(NOW.plusHours(24).plusMinutes(5), AppointmentStatus.CONFIRMED);
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW);
        whenCandidates(appointment);

        job.dispatchReminders(NOW);

        verify(appointmentMailEventPublisher, never()).publishReminder(any());
    }

    @Test
    void appointmentConfirmed20hBeforeVisitStillPublishes6hReminderIn6hWindow() {
        Appointment appointment = appointmentAt(NOW.plusHours(6).plusMinutes(5), AppointmentStatus.CONFIRMED);
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW.minusHours(14));
        whenCandidates(appointment);

        job.dispatchReminders(NOW);

        assertThat(singlePublishedEvent().templateCode()).isEqualTo(AppointmentReminderJob.REMINDER_6H_TEMPLATE);
    }

    @Test
    void appointmentConfirmedOnly6hBeforeVisitDoesNotPublish6hReminder() {
        Appointment appointment = appointmentAt(NOW.plusHours(6).plusMinutes(5), AppointmentStatus.CONFIRMED);
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW);
        whenCandidates(appointment);

        job.dispatchReminders(NOW);

        verify(appointmentMailEventPublisher, never()).publishReminder(any());
    }

    @Test
    void appointmentUnder4hAwayDoesNotPublishReminder() {
        Appointment appointment = appointmentAt(NOW.plusHours(3), AppointmentStatus.CONFIRMED);
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW.minusDays(1));
        whenCandidates(appointment);

        job.dispatchReminders(NOW);

        verify(appointmentMailEventPublisher, never()).publishReminder(any());
    }

    @Test
    void duplicate24hPendingOrSentDoesNotPublish24hAgain() {
        Appointment appointment = appointmentAt(NOW.plusHours(24).plusMinutes(5), AppointmentStatus.CONFIRMED);
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW.minusHours(6));
        whenCandidates(appointment);
        when(notificationRepository.existsByAppointment_IdAndTemplateCodeAndStatusIn(
                eq(appointment.getId()),
                eq(AppointmentReminderJob.REMINDER_24H_TEMPLATE),
                any()
        )).thenReturn(true);

        job.dispatchReminders(NOW);

        verify(appointmentMailEventPublisher, never()).publishReminder(any());
    }

    @Test
    void sent24hDoesNotBlock6hReminder() {
        Appointment appointment = appointmentAt(NOW.plusHours(6).plusMinutes(5), AppointmentStatus.CONFIRMED);
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW.minusHours(3));
        whenCandidates(appointment);

        job.dispatchReminders(NOW);

        assertThat(singlePublishedEvent().templateCode()).isEqualTo(AppointmentReminderJob.REMINDER_6H_TEMPLATE);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"REQUESTED", "CANCELLED", "CHECKED_IN", "COMPLETED", "NO_SHOW"})
    void nonConfirmedAppointmentDoesNotPublishReminder(AppointmentStatus status) {
        Appointment appointment = appointmentAt(NOW.plusHours(6).plusMinutes(5), status);
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW.minusHours(3));
        whenCandidates(appointment);

        job.dispatchReminders(NOW);

        verify(appointmentMailEventPublisher, never()).publishReminder(any());
    }

    @Test
    void missingEmailDoesNotPublishReminder() {
        Appointment appointment = appointmentAt(NOW.plusHours(6).plusMinutes(5), AppointmentStatus.CONFIRMED);
        appointment.setPatientEmail(" ");
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW.minusHours(3));
        whenCandidates(appointment);

        job.dispatchReminders(NOW);

        verify(appointmentMailEventPublisher, never()).publishReminder(any());
    }

    @Test
    void missingEtaStartDoesNotPublishReminder() {
        Appointment appointment = appointmentAt(NOW.plusHours(6).plusMinutes(5), AppointmentStatus.CONFIRMED);
        appointment.setEtaStart(null);
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW.minusHours(3));
        whenCandidates(appointment);

        job.dispatchReminders(NOW);

        verify(appointmentMailEventPublisher, never()).publishReminder(any());
    }

    @Test
    void allowEmailFalsePreferenceDoesNotPublishReminder() {
        Appointment appointment = linkedPatientAppointment(NOW.plusHours(6).plusMinutes(5));
        whenCandidates(appointment);
        when(userRepository.findByPatient_Id(11L)).thenReturn(Optional.of(User.builder().id(21L).build()));
        when(notificationPreferenceRepository.findByUser_Id(21L)).thenReturn(Optional.of(preference(false, true)));

        job.dispatchReminders(NOW);

        verify(appointmentMailEventPublisher, never()).publishReminder(any());
    }

    @Test
    void appointmentRemindersFalsePreferenceDoesNotPublishReminder() {
        Appointment appointment = linkedPatientAppointment(NOW.plusHours(6).plusMinutes(5));
        whenCandidates(appointment);
        when(userRepository.findByPatient_Id(11L)).thenReturn(Optional.of(User.builder().id(21L).build()));
        when(notificationPreferenceRepository.findByUser_Id(21L)).thenReturn(Optional.of(preference(true, false)));

        job.dispatchReminders(NOW);

        verify(appointmentMailEventPublisher, never()).publishReminder(any());
    }

    @Test
    void enabledPreferencePublishesReminder() {
        Appointment appointment = linkedPatientAppointment(NOW.plusHours(6).plusMinutes(5));
        whenCandidates(appointment);
        when(userRepository.findByPatient_Id(11L)).thenReturn(Optional.of(User.builder().id(21L).build()));
        when(notificationPreferenceRepository.findByUser_Id(21L)).thenReturn(Optional.of(preference(true, true)));

        job.dispatchReminders(NOW);

        assertThat(singlePublishedEvent().templateCode()).isEqualTo(AppointmentReminderJob.REMINDER_6H_TEMPLATE);
    }

    private Appointment linkedPatientAppointment(LocalDateTime appointmentStart) {
        Appointment appointment = appointmentAt(appointmentStart, AppointmentStatus.CONFIRMED);
        appointment.setPatient(Patient.builder().id(11L).build());
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW.minusHours(3));
        return appointment;
    }

    private NotificationPreference preference(boolean allowEmail, boolean appointmentReminders) {
        return NotificationPreference.builder()
                .allowEmail(allowEmail)
                .appointmentReminders(appointmentReminders)
                .build();
    }

    private Appointment appointmentAt(LocalDateTime appointmentStart, AppointmentStatus status) {
        Branch branch = Branch.builder()
                .id(1L)
                .nameVn("PrimeCare Central")
                .addressVn("123 Nguyen Trai")
                .phone("1900 0000")
                .build();
        Specialty specialty = Specialty.builder()
                .id(2L)
                .nameVn("Tim mạch")
                .build();
        DoctorProfile doctor = DoctorProfile.builder()
                .id(3L)
                .fullName("BS Nguyen Van A")
                .build();
        Appointment appointment = Appointment.builder()
                .id(10L)
                .code("APT-10")
                .status(status)
                .branch(branch)
                .specialty(specialty)
                .doctor(doctor)
                .visitDate(appointmentStart.toLocalDate())
                .etaStart(appointmentStart.toLocalTime())
                .etaEnd(appointmentStart.toLocalTime().plusMinutes(30))
                .patientFullName("Patient Test")
                .patientEmail("patient@example.test")
                .patientPhone("0912345678")
                .build();
        appointment.setCreatedAt(NOW.minusDays(2));
        appointment.setConfirmedAt(NOW.minusHours(3));
        return appointment;
    }

    private void whenCandidates(Appointment... appointments) {
        when(appointmentRepository.findForEmailReminderCandidates(any(), any(), any()))
                .thenReturn(List.of(appointments));
    }

    private AppointmentReminderMailEvent singlePublishedEvent() {
        ArgumentCaptor<AppointmentReminderMailEvent> captor = ArgumentCaptor.forClass(AppointmentReminderMailEvent.class);
        verify(appointmentMailEventPublisher).publishReminder(captor.capture());
        return captor.getValue();
    }
}
