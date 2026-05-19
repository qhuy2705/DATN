package com.PrimeCare.PrimeCare.modules.notification.messaging;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentSlotHoldRepository;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentReminderMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.AppointmentConfirmedMailReadyEvent;
import com.PrimeCare.PrimeCare.modules.notification.service.EmailNotificationService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentMailConsumerTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentSlotHoldRepository slotHoldRepository;
    @Mock
    private EmailNotificationService emailNotificationService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private InternalNotificationService internalNotificationService;

    private AppointmentMailConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AppointmentMailConsumer(
                appointmentRepository,
                slotHoldRepository,
                emailNotificationService,
                fileStorageService,
                internalNotificationService
        );
        ReflectionTestUtils.setField(consumer, "frontendBaseUrl", "http://localhost:8081");
    }

    @Test
    void handleAppointmentReminderSendsEmailWithEventTemplateCode() {
        Appointment appointment = confirmedAppointment();
        AppointmentReminderMailEvent event = reminderEvent("APPOINTMENT_REMINDER_6H", "6H");
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));

        consumer.handleAppointmentReminder(event);

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailNotificationService).sendHtmlEmail(
                eq(appointment),
                eq("patient@example.test"),
                eq("PrimeCare nhắc bạn có lịch khám trong khoảng 6 giờ tới"),
                htmlCaptor.capture(),
                eq("APPOINTMENT_REMINDER_6H"),
                isNull(),
                isNull()
        );
        assertThat(htmlCaptor.getValue())
                .contains("Hạn cuối tự hủy")
                .contains("14/05/2026 04:00")
                .contains("Hệ thống chỉ hỗ trợ tự hủy lịch trước giờ khám ít nhất 4 tiếng");
    }

    @Test
    void handleAppointmentReminderSkipsWhenAppointmentNoLongerConfirmed() {
        Appointment appointment = confirmedAppointment();
        appointment.setStatus(AppointmentStatus.CANCELLED);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));

        consumer.handleAppointmentReminder(reminderEvent("APPOINTMENT_REMINDER_24H", "24H"));

        verify(emailNotificationService, never()).sendHtmlEmail(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleAppointmentReminderSkipsWhenEmailMissing() {
        Appointment appointment = confirmedAppointment();
        appointment.setPatientEmail(" ");
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));

        consumer.handleAppointmentReminder(new AppointmentReminderMailEvent(
                10L,
                "APT-10",
                "Patient Test",
                " ",
                "BS Nguyen Van A",
                "Tim mạch",
                "PrimeCare Central",
                "2026-05-14",
                "08:00",
                "08:30",
                "14/05/2026 04:00",
                "APPOINTMENT_REMINDER_24H",
                "24H"
        ));

        verify(emailNotificationService, never()).sendHtmlEmail(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleAppointmentReminderSkipsLegacySharedTemplate() {
        Appointment appointment = confirmedAppointment();
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));

        consumer.handleAppointmentReminder(reminderEvent("APPOINTMENT_REMINDER", "24H"));

        verify(emailNotificationService, never()).sendHtmlEmail(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleAppointmentConfirmedMailReadyKeepsPdfAndAddsCancellationDeadline() {
        Appointment appointment = confirmedAppointment();
        appointment.setVisitDate(LocalDate.of(2099, 5, 14));
        byte[] pdf = "pdf".getBytes();
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));
        when(fileStorageService.downloadAsBytes("appointments/APT-10.pdf")).thenReturn(pdf);

        consumer.handleAppointmentConfirmedMailReady(new AppointmentConfirmedMailReadyEvent(
                10L,
                20L,
                "patient@example.test",
                "Patient Test",
                "APT-10",
                "PrimeCare Central",
                "BS Nguyen Van A",
                "Tim mạch",
                "2099-05-14",
                "AM",
                "08:00",
                "08:30",
                "appointments/APT-10.pdf"
        ));

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailNotificationService).sendHtmlEmail(
                eq(appointment),
                eq("patient@example.test"),
                eq("Lịch khám PrimeCare của bạn đã được xác nhận"),
                htmlCaptor.capture(),
                eq("APPOINTMENT_CONFIRMED"),
                eq(pdf),
                eq("phieu-hen-APT-10.pdf")
        );
        assertThat(htmlCaptor.getValue())
                .contains("Hạn cuối tự hủy")
                .contains("14/05/2099 04:00")
                .contains("Bạn có thể tự hủy lịch trước");
    }

    private AppointmentReminderMailEvent reminderEvent(String templateCode, String reminderKind) {
        return new AppointmentReminderMailEvent(
                10L,
                "APT-10",
                "Patient Test",
                "patient@example.test",
                "BS Nguyen Van A",
                "Tim mạch",
                "PrimeCare Central",
                "2026-05-14",
                "08:00",
                "08:30",
                "14/05/2026 04:00",
                templateCode,
                reminderKind
        );
    }

    private Appointment confirmedAppointment() {
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
        return Appointment.builder()
                .id(10L)
                .code("APT-10")
                .status(AppointmentStatus.CONFIRMED)
                .branch(branch)
                .specialty(specialty)
                .doctor(doctor)
                .visitDate(LocalDate.of(2026, 5, 14))
                .etaStart(LocalTime.of(8, 0))
                .etaEnd(LocalTime.of(8, 30))
                .patientFullName("Patient Test")
                .patientEmail("patient@example.test")
                .patientPhone("0912345678")
                .build();
    }
}
