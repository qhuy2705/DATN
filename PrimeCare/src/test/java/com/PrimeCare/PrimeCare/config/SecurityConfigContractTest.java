package com.PrimeCare.PrimeCare.config;

import com.PrimeCare.PrimeCare.modules.appointment.controller.DoctorAppointmentController;
import com.PrimeCare.PrimeCare.modules.billing.controller.CashierInvoiceController;
import com.PrimeCare.PrimeCare.modules.encounter.controller.DoctorEncounterController;
import com.PrimeCare.PrimeCare.modules.encounter.dto.request.UpdateEncounterRequest;
import com.PrimeCare.PrimeCare.modules.pharmacy.controller.PharmacyController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.nio.file.Files;
import java.nio.file.Path;

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
        assertThat(source).contains(".requestMatchers(\"/api/pharmacy/**\").hasAnyRole(\"PHARMACIST\", \"OPERATIONS_ADMIN\")");
        assertThat(source).contains(".requestMatchers(\"/api/patient/**\").hasRole(\"PATIENT\")");
        assertThat(source).contains(".requestMatchers(\"/api/doctor/appointments/**\").hasRole(\"DOCTOR\")");
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
                .value()).isEqualTo("hasAnyRole('PHARMACIST', 'OPERATIONS_ADMIN')");

        assertThat(CashierInvoiceController.class
                .getMethod("summary", java.time.LocalDate.class, java.time.LocalDate.class, java.time.LocalDate.class)
                .getAnnotation(PreAuthorize.class)
                .value()).isEqualTo("hasRole('CASHIER')");
    }

    private String securityConfigSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/PrimeCare/PrimeCare/config/SecurityConfig.java"));
    }
}
