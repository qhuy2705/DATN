package com.PrimeCare.PrimeCare.modules.prescription.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.medication.repository.DrugInteractionRepository;
import com.PrimeCare.PrimeCare.modules.medication.repository.MedicationRepository;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientAllergyRepository;
import com.PrimeCare.PrimeCare.modules.pharmacy.service.InventoryService;
import com.PrimeCare.PrimeCare.modules.prescription.dto.response.PrescriptionResponse;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
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
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionServicePharmacyListTest {

    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private MedicationRepository medicationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ServiceOrderRepository serviceOrderRepository;
    @Mock
    private PatientAllergyRepository patientAllergyRepository;
    @Mock
    private DrugInteractionRepository drugInteractionRepository;
    @Mock
    private PrescriptionPdfService prescriptionPdfService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private PrescriptionService service;

    @Test
    void markAsDispensedDoesNotDeductStockAgainForAlreadyDispensedPrescription() {
        Prescription prescription = prescription(1L, "RX-1", PrescriptionStatus.DISPENSED, "BN001", "Nguyen Van A");
        when(prescriptionRepository.findWithDetailsById(1L)).thenReturn(java.util.Optional.of(prescription));

        assertThatThrownBy(() -> service.markAsDispensed(1L, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_INVALID_STATUS)
                );

        verify(inventoryService, never()).dispenseFIFO(anyLong(), anyInt(), anyLong(), anyLong());
    }

    @Test
    void listForPharmacyReturnsAllStatusesWhenStatusMissing() {
        Pageable pageable = pharmacyPageable();
        Prescription issued = prescription(1L, "RX-1", PrescriptionStatus.ISSUED, "BN001", "Nguyen Van A");
        Prescription paid = prescription(2L, "RX-2", PrescriptionStatus.PAID, "BN002", "Tran Thi B");
        Prescription dispensed = prescription(3L, "RX-3", PrescriptionStatus.DISPENSED, "BN003", "Le Van C");
        when(prescriptionRepository.findIdsForPharmacy(null, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(1L, 2L, 3L), pageable, 3));
        when(prescriptionRepository.findAllWithDetailsByIdIn(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(dispensed, issued, paid));

        var response = service.listForPharmacy(null, " ", pageable);

        assertThat(response.getItems())
                .extracting(PrescriptionResponse::getStatus)
                .containsExactly(PrescriptionStatus.ISSUED, PrescriptionStatus.PAID, PrescriptionStatus.DISPENSED);
        assertThat(response.getItems())
                .extracting(PrescriptionResponse::getEncounterCode)
                .containsExactly("ENC-1", "ENC-2", "ENC-3");
        verify(prescriptionRepository).findIdsForPharmacy(null, null, null, null, null, pageable);
    }

    @Test
    void listForPharmacyFiltersPaidStatus() {
        Pageable pageable = pharmacyPageable();
        Prescription prescription = prescription(2L, "RX-2", PrescriptionStatus.PAID, "BN002", "Tran Thi B");
        when(prescriptionRepository.findIdsForPharmacy(PrescriptionStatus.PAID, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(2L), pageable, 1));
        when(prescriptionRepository.findAllWithDetailsByIdIn(List.of(2L)))
                .thenReturn(List.of(prescription));

        var response = service.listForPharmacy(PrescriptionStatus.PAID, null, pageable);

        assertThat(response.getItems())
                .extracting(PrescriptionResponse::getStatus)
                .containsExactly(PrescriptionStatus.PAID);
        verify(prescriptionRepository).findIdsForPharmacy(PrescriptionStatus.PAID, null, null, null, null, pageable);
    }

    @Test
    void listForPharmacyFiltersDispensedStatus() {
        Pageable pageable = pharmacyPageable();
        Prescription prescription = prescription(3L, "RX-3", PrescriptionStatus.DISPENSED, "BN003", "Le Van C");
        when(prescriptionRepository.findIdsForPharmacy(PrescriptionStatus.DISPENSED, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(3L), pageable, 1));
        when(prescriptionRepository.findAllWithDetailsByIdIn(List.of(3L)))
                .thenReturn(List.of(prescription));

        var response = service.listForPharmacy(PrescriptionStatus.DISPENSED, null, pageable);

        assertThat(response.getItems())
                .extracting(PrescriptionResponse::getStatus)
                .containsExactly(PrescriptionStatus.DISPENSED);
        verify(prescriptionRepository).findIdsForPharmacy(PrescriptionStatus.DISPENSED, null, null, null, null, pageable);
    }

    @Test
    void listForPharmacySearchesByTrimmedKeyword() {
        Pageable pageable = pharmacyPageable();
        Prescription prescription = prescription(4L, "RX-4", PrescriptionStatus.CANCELLED, "BN001", "Nguyen Van A");
        when(prescriptionRepository.findIdsForPharmacy(null, "%bn001%", "BN001%", "BN001", null, pageable))
                .thenReturn(new PageImpl<>(List.of(4L), pageable, 1));
        when(prescriptionRepository.findAllWithDetailsByIdIn(List.of(4L)))
                .thenReturn(List.of(prescription));

        var response = service.listForPharmacy(null, "  BN001  ", pageable);

        assertThat(response.getItems())
                .extracting(PrescriptionResponse::getId)
                .containsExactly(4L);
        verify(prescriptionRepository).findIdsForPharmacy(null, "%bn001%", "BN001%", "BN001", null, pageable);
    }

    private Pageable pharmacyPageable() {
        return PageRequest.of(0, 20, Sort.by("createdAt").descending());
    }

    private Prescription prescription(
            Long id,
            String code,
            PrescriptionStatus status,
            String patientCode,
            String patientName
    ) {
        Patient patient = Patient.builder()
                .id(10L + id)
                .code(patientCode)
                .fullName(patientName)
                .phone("0900000000")
                .build();
        DoctorProfile doctor = DoctorProfile.builder()
                .id(20L + id)
                .fullName("Dr Test")
                .build();
        Encounter encounter = Encounter.builder()
                .id(30L + id)
                .code("ENC-" + id)
                .patient(patient)
                .doctor(doctor)
                .patientFullNameSnapshot(patientName)
                .build();
        User doctorUser = User.builder()
                .id(40L + id)
                .doctorProfile(doctor)
                .build();

        return Prescription.builder()
                .id(id)
                .code(code)
                .encounter(encounter)
                .doctorUser(doctorUser)
                .issuedDate(LocalDate.now())
                .status(status)
                .items(new ArrayList<>())
                .build();
    }
}
