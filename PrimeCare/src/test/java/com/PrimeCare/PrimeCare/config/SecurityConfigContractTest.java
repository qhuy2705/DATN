package com.PrimeCare.PrimeCare.config;

import com.PrimeCare.PrimeCare.modules.appointment.controller.DoctorAppointmentController;
import com.PrimeCare.PrimeCare.modules.appointment.controller.AdminAppointmentController;
import com.PrimeCare.PrimeCare.modules.appointment.controller.ReceptionAppointmentController;
import com.PrimeCare.PrimeCare.modules.appointment.controller.ReceptionFollowUpController;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.AppointmentCheckInRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.AppointmentNoShowRequest;
import com.PrimeCare.PrimeCare.modules.appointment.dto.request.FollowUpResolveRequest;
import com.PrimeCare.PrimeCare.modules.audit.controller.AdminAuditLogController;
import com.PrimeCare.PrimeCare.modules.billing.controller.CashierInvoiceController;
import com.PrimeCare.PrimeCare.modules.booking_restriction.controller.AdminBookingRestrictionController;
import com.PrimeCare.PrimeCare.modules.encounter.controller.DoctorEncounterController;
import com.PrimeCare.PrimeCare.modules.encounter.dto.request.UpdateEncounterRequest;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.controller.AdminDoctorController;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request.DoctorOptionMode;
import com.PrimeCare.PrimeCare.modules.pharmacy.controller.InventoryController;
import com.PrimeCare.PrimeCare.modules.pharmacy.controller.PharmacyController;
import com.PrimeCare.PrimeCare.modules.service_result.controller.ServiceResultController;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigContractTest {

    @Test
    void securityConfigKeepsSpecificDoctorEncounterMatchersBeforeBroadMatcher() throws Exception {
        String source = securityConfigSource();

        assertThat(source.indexOf("HttpMethod.POST, \"/api/doctor/encounters/*/reopen\""))
                .isLessThan(source.indexOf("\"/api/doctor/encounters/**\""));
        assertThat(source.indexOf("\"/api/doctor/encounters/*/prescriptions/**\""))
                .isLessThan(source.indexOf("\"/api/doctor/encounters/**\""));
    }

    @Test
    void securityConfigDocumentsRouteLevelRbacForCriticalRoles() throws Exception {
        String source = securityConfigSource();

        assertThat(source).contains(".requestMatchers(\"/api/public/**\").permitAll()");
        assertThat(source).contains(".requestMatchers(\"/api/cashier/**\").hasRole(\"CASHIER\")");
        assertThat(source).doesNotContain(".requestMatchers(\"/api/cashier/**\").hasAnyRole");
        assertThat(source).contains(".requestMatchers(\"/api/pharmacy/**\").hasRole(\"PHARMACIST\")");
        assertThat(source).doesNotContain(".requestMatchers(\"/api/pharmacy/**\").hasAnyRole");
        assertThat(source).contains(".requestMatchers(HttpMethod.GET, \"/api/service-desk/results/*/pdf\").hasRole(\"SERVICE_TECHNICIAN\")");
        assertThat(source).contains(".requestMatchers(\"/api/service-desk/**\").hasRole(\"SERVICE_TECHNICIAN\")");
        assertThat(source).contains(".requestMatchers(\"/api/patient/**\").hasRole(\"PATIENT\")");
        assertThat(source).contains(".requestMatchers(\"/api/doctor/appointments/**\").hasRole(\"DOCTOR\")");
    }

    @Test
    void bookingRestrictionAdminAllowsOnlyOperationsAdminBeforeAdminCatchAll() throws Exception {
        String source = securityConfigSource();

        assertThat(source).contains(".requestMatchers(\"/api/admin/booking-restrictions\", \"/api/admin/booking-restrictions/**\").hasRole(\"OPERATIONS_ADMIN\")");
        assertThat(source.indexOf("\"/api/admin/booking-restrictions\""))
                .isLessThan(source.indexOf("\"/api/admin/**\""));

        PreAuthorize guard = AdminBookingRestrictionController.class.getAnnotation(PreAuthorize.class);
        assertThat(guard.value()).isEqualTo("hasRole('OPERATIONS_ADMIN')");
    }

    @Test
    void auditLogRoutesAllowOnlySystemAdmin() throws Exception {
        String source = securityConfigSource();

        assertThat(source).contains(".requestMatchers(\"/api/admin/audit-logs\", \"/api/admin/audit-logs/**\").hasRole(\"SYSTEM_ADMIN\")");
        assertThat(source).doesNotContain(".requestMatchers(\"/api/admin/audit-logs/**\").hasAnyRole(\"SYSTEM_ADMIN\", \"OPERATIONS_ADMIN\")");
        assertThat(source).doesNotContain(".requestMatchers(\"/api/admin/audit-logs\", \"/api/admin/audit-logs/**\").permitAll()");
        assertThat(source.indexOf("\"/api/admin/audit-logs\""))
                .isLessThan(source.indexOf("\"/api/admin/**\""));

        PreAuthorize guard = AdminAuditLogController.class
                .getMethod(
                        "search",
                        java.time.LocalDate.class,
                        String.class,
                        String.class,
                        Long.class,
                        Long.class,
                        String.class,
                        String.class,
                        String.class,
                        java.time.LocalDate.class,
                        java.time.LocalDate.class,
                        Pageable.class
                )
                .getAnnotation(PreAuthorize.class);
        assertThat(guard.value()).isEqualTo("hasRole('SYSTEM_ADMIN')");
        assertThat(guard.value()).doesNotContain(
                "OPERATIONS_ADMIN",
                "STAFF",
                "DOCTOR",
                "CASHIER",
                "PHARMACIST",
                "SERVICE_TECHNICIAN",
                "PATIENT"
        );
    }

    @Test
    void internalDoctorOptionsAreProtectedForStaffAndAdminsOnly() throws Exception {
        String source = securityConfigSource();

        assertThat(source).contains(".requestMatchers(HttpMethod.GET, \"/api/admin/doctors/options\").hasAnyRole(\"SYSTEM_ADMIN\", \"OPERATIONS_ADMIN\", \"STAFF\")");
        assertThat(source.indexOf("HttpMethod.GET, \"/api/admin/doctors/options\""))
                .isLessThan(source.indexOf("\"/api/admin/**\""));
        assertThat(source).doesNotContain(".requestMatchers(HttpMethod.GET, \"/api/admin/doctors/options\").permitAll()");

        PreAuthorize guard = AdminDoctorController.class
                .getMethod("options", Long.class, Long.class, DoctorStatus.class, Boolean.class, String.class, DoctorOptionMode.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(guard.value()).isEqualTo("hasAnyRole('SYSTEM_ADMIN','OPERATIONS_ADMIN','STAFF')");
        assertThat(guard.value()).doesNotContain("PATIENT");
    }

    @Test
    void appointmentOperationRoutesExcludeSystemAdminBeforeAdminCatchAll() throws Exception {
        String source = securityConfigSource();

        assertThat(source).contains(".requestMatchers(HttpMethod.POST, \"/api/admin/appointments/check-in\").hasRole(\"STAFF\")");
        assertThat(source).contains(".requestMatchers(\"/api/admin/appointments\", \"/api/admin/appointments/**\").hasAnyRole(\"OPERATIONS_ADMIN\", \"STAFF\")");
        assertThat(source).doesNotContain(".requestMatchers(\"/api/admin/appointments\", \"/api/admin/appointments/**\").hasAnyRole(\"SYSTEM_ADMIN\", \"OPERATIONS_ADMIN\", \"STAFF\")");
        assertThat(source.indexOf("HttpMethod.POST, \"/api/admin/appointments/check-in\""))
                .isLessThan(source.indexOf("\"/api/admin/appointments\", \"/api/admin/appointments/**\""));
        assertThat(source.indexOf("\"/api/admin/appointments\""))
                .isLessThan(source.indexOf("\"/api/admin/**\""));

        assertThat(AdminAppointmentController.class
                .getMethod("search", AppointmentStatus.class, java.time.LocalDate.class, Long.class, Long.class, Long.class, Boolean.class, Boolean.class, String.class, Pageable.class)
                .getAnnotation(PreAuthorize.class)
                .value()).isEqualTo("hasAnyRole('OPERATIONS_ADMIN','STAFF')");
        assertThat(AdminAppointmentController.class
                .getMethod("resolveNoShow", Long.class, Authentication.class, AppointmentNoShowRequest.class)
                .getAnnotation(PreAuthorize.class)
                .value()).isEqualTo("hasAnyRole('OPERATIONS_ADMIN','STAFF')");
        assertThat(AdminAppointmentController.class
                .getMethod("resolveFollowUp", Long.class, Authentication.class, AppointmentNoShowRequest.class)
                .getAnnotation(PreAuthorize.class)
                .value()).isEqualTo("hasAnyRole('OPERATIONS_ADMIN','STAFF')");
        assertThat(AdminAppointmentController.class
                .getMethod("checkIn", Authentication.class, AppointmentCheckInRequest.class)
                .isAnnotationPresent(Deprecated.class)).isTrue();
        assertThat(AdminAppointmentController.class
                .getMethod("checkIn", Authentication.class, AppointmentCheckInRequest.class)
                .getAnnotation(PreAuthorize.class)
                .value()).isEqualTo("hasRole('STAFF')");
    }

    @Test
    void receptionRoutesAllowOnlyStaff() throws Exception {
        String source = securityConfigSource();

        assertThat(source).contains(".requestMatchers(\"/api/reception/**\").hasRole(\"STAFF\")");
        assertThat(source).doesNotContain(".requestMatchers(\"/api/reception/**\").hasAnyRole(\"OPERATIONS_ADMIN\", \"STAFF\")");
        assertThat(source).doesNotContain(".requestMatchers(\"/api/reception/**\").hasAnyRole(\"SYSTEM_ADMIN\", \"OPERATIONS_ADMIN\", \"STAFF\")");

        assertThat(ReceptionFollowUpController.class
                .getMethod("queue", String.class, String.class, String.class, Pageable.class)
                .getAnnotation(PreAuthorize.class)
                .value()).isEqualTo("hasRole('STAFF')");
        assertThat(ReceptionFollowUpController.class
                .getMethod("resolve", Long.class, Authentication.class, FollowUpResolveRequest.class)
                .getAnnotation(PreAuthorize.class)
                .value()).isEqualTo("hasRole('STAFF')");

        String guard = ReceptionFollowUpController.class
                .getMethod("queue", String.class, String.class, String.class, Pageable.class)
                .getAnnotation(PreAuthorize.class)
                .value();
        assertThat(guard).contains("STAFF");
        assertThat(guard).doesNotContain("SYSTEM_ADMIN", "OPERATIONS_ADMIN", "PATIENT", "DOCTOR", "CASHIER", "PHARMACIST", "SERVICE_TECHNICIAN");

        assertThat(ReceptionAppointmentController.class
                .getMethod("qrCheckIn", Authentication.class, AppointmentCheckInRequest.class)
                .getAnnotation(PreAuthorize.class)
                .value()).isEqualTo("hasRole('STAFF')");
        assertThat(ReceptionAppointmentController.class
                .getMethod("markArrived", Long.class, Authentication.class)
                .isAnnotationPresent(Deprecated.class)).isTrue();
        assertControllerEndpointsUseOnlyRole(ReceptionAppointmentController.class, "STAFF");
    }

    @Test
    void doctorEncounterEditIsDoctorOnlyButReopenAllowsOperationsAdmin() throws Exception {
        PreAuthorize update = DoctorEncounterController.class
                .getMethod("update", Long.class, Authentication.class, UpdateEncounterRequest.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize reopen = DoctorEncounterController.class
                .getMethod("reopenEncounter", Long.class, String.class, Authentication.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(update.value()).isEqualTo("hasRole('DOCTOR')");
        assertThat(reopen.value()).isEqualTo("hasAnyRole('DOCTOR', 'OPERATIONS_ADMIN')");
    }

    @Test
    void doctorPharmacyAndCashierControllersKeepMethodLevelRoleGuards() throws Exception {
        assertThat(DoctorAppointmentController.class
                .getMethod("myAppointmentSummary", Authentication.class, java.time.LocalDate.class, java.time.LocalDate.class, java.time.LocalDate.class)
                .getAnnotation(PreAuthorize.class)
                .value()).isEqualTo("hasRole('DOCTOR')");

        assertThat(PharmacyController.class
                .getMethod("dispensePrescription", Long.class, Authentication.class)
                .getAnnotation(PreAuthorize.class)
                .value()).isEqualTo("hasRole('PHARMACIST')");

        assertThat(CashierInvoiceController.class
                .getMethod("summary", java.time.LocalDate.class, java.time.LocalDate.class, java.time.LocalDate.class)
                .getAnnotation(PreAuthorize.class)
                .value()).isEqualTo("hasRole('CASHIER')");
    }

    @Test
    void cashierControllerEndpointsRemainCashierOnly() {
        Arrays.stream(CashierInvoiceController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class))
                .forEach(method -> {
                    PreAuthorize guard = method.getAnnotation(PreAuthorize.class);

                    assertThat(guard)
                            .as(method.getName() + " must declare a method-level cashier guard")
                            .isNotNull();
                    assertThat(guard.value())
                            .as(method.getName() + " must be CASHIER-only")
                            .isEqualTo("hasRole('CASHIER')");
                    assertThat(guard.value())
                            .as(method.getName() + " must not include operations admin")
                            .doesNotContain("OPERATIONS_ADMIN");
                });
    }

    @Test
    void pharmacyControllerEndpointsRemainPharmacistOnly() {
        assertControllerEndpointsUseOnlyRole(PharmacyController.class, "PHARMACIST");
        assertControllerEndpointsUseOnlyRole(InventoryController.class, "PHARMACIST");
    }

    @Test
    void serviceResultWriteEndpointsRemainServiceTechnicianOnly() throws Exception {
        assertControllerEndpointsUseOnlyRole(ServiceResultController.class, "SERVICE_TECHNICIAN");

        var writeMethods = Arrays.stream(ServiceResultController.class.getDeclaredMethods())
                                 .filter(method -> method.isAnnotationPresent(PostMapping.class))
                                 .toList();
        assertThat(writeMethods)
                .extracting(method -> method.getName())
                .containsExactlyInAnyOrder("submit", "verify");

        writeMethods
                .forEach(method -> {
                    PreAuthorize guard = method.getAnnotation(PreAuthorize.class);

                    assertThat(guard)
                            .as(method.getName() + " must declare a method-level service technician guard")
                            .isNotNull();
                    assertThat(guard.value())
                            .as(method.getName() + " must be SERVICE_TECHNICIAN-only")
                            .isEqualTo("hasRole('SERVICE_TECHNICIAN')");
                    assertThat(guard.value())
                            .as(method.getName() + " must not include operations admin")
                            .doesNotContain("OPERATIONS_ADMIN");
                });

        assertThat(ServiceResultController.class
                .getMethod("downloadPdf", Long.class, Authentication.class)
                .getAnnotation(PreAuthorize.class)
                .value()).isEqualTo("hasRole('SERVICE_TECHNICIAN')");
    }

    private void assertControllerEndpointsUseOnlyRole(Class<?> controllerClass, String role) {
        assertControllerEndpointsUseOnlyRole(controllerClass, role, new String[0]);
    }

    private void assertControllerEndpointsUseOnlyRole(Class<?> controllerClass, String role, String... excludedMethodNames) {
        var excluded = Arrays.asList(excludedMethodNames);
        Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class))
                .filter(method -> !excluded.contains(method.getName()))
                .forEach(method -> {
                    PreAuthorize guard = method.getAnnotation(PreAuthorize.class);

                    assertThat(guard)
                            .as(controllerClass.getSimpleName() + "." + method.getName() + " must declare a method-level role guard")
                            .isNotNull();
                    assertThat(guard.value())
                            .as(controllerClass.getSimpleName() + "." + method.getName() + " must be " + role + "-only")
                            .isEqualTo("hasRole('" + role + "')");
                    assertThat(guard.value())
                            .as(controllerClass.getSimpleName() + "." + method.getName() + " must not include operations admin")
                            .doesNotContain("OPERATIONS_ADMIN");
                });
    }

    private String securityConfigSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/PrimeCare/PrimeCare/config/SecurityConfig.java"));
    }
}
