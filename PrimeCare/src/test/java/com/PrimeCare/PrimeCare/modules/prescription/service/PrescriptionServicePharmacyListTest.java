package com.PrimeCare.PrimeCare.modules.prescription.service;

import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.medication.entity.Medication;
import com.PrimeCare.PrimeCare.modules.medication.repository.DrugInteractionRepository;
import com.PrimeCare.PrimeCare.modules.medication.repository.MedicationRepository;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient.repository.PatientAllergyRepository;
import com.PrimeCare.PrimeCare.modules.pharmacy.service.InventoryService;
import com.PrimeCare.PrimeCare.modules.prescription.dto.response.PrescriptionResponse;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.prescription.entity.PrescriptionItem;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionItemStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(prescriptionRepository.findWithLockDetailsById(1L)).thenReturn(Optional.of(prescription));

        assertThatThrownBy(() -> service.markAsDispensed(1L, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_INVALID_STATUS);
                    assertThat(ex.getMessage()).isEqualTo("Prescription has already been dispensed.");
                });

        verify(inventoryService, never()).dispenseFIFO(anyLong(), anyInt(), anyLong(), anyLong());
        verifyNoInteractions(inventoryService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void markAsDispensedDispensesEligiblePaidPrescription() {
        Prescription prescription = prescription(8L, "RX-8", PrescriptionStatus.PAID, "BN008", "Nguyen Van B");
        addItem(prescription, 501L, 4);
        addItem(prescription, 501L, 2);
        User dispenser = User.builder().id(99L).build();
        when(prescriptionRepository.findWithLockDetailsById(8L)).thenReturn(Optional.of(prescription));
        when(userRepository.findById(99L)).thenReturn(Optional.of(dispenser));
        when(prescriptionRepository.save(prescription)).thenReturn(prescription);

        PrescriptionResponse response = service.markAsDispensed(8L, 99L);

        assertThat(response.getStatus()).isEqualTo(PrescriptionStatus.DISPENSED);
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.DISPENSED);
        verify(inventoryService).validateDispenseAvailability(501L, 6);
        verify(inventoryService).dispenseFIFO(501L, 4, 8L, 99L);
        verify(inventoryService).dispenseFIFO(501L, 2, 8L, 99L);
        verify(auditLogService).log(eq(dispenser), eq("DISPENSE_PRESCRIPTION"), eq("PRESCRIPTION"), eq(8L), any(), any());
    }

    @Test
    void markAsDispensedRejectsCancelledPrescription() {
        Prescription prescription = prescription(9L, "RX-9", PrescriptionStatus.CANCELLED, "BN009", "Tran Thi C");
        when(prescriptionRepository.findWithLockDetailsById(9L)).thenReturn(Optional.of(prescription));

        assertThatThrownBy(() -> service.markAsDispensed(9L, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_INVALID_STATUS)
                );

        verify(inventoryService, never()).dispenseFIFO(anyLong(), anyInt(), anyLong(), anyLong());
        verifyNoInteractions(inventoryService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void markAsDispensedRejectsUnpaidPrescription() {
        Prescription prescription = prescription(10L, "RX-10", PrescriptionStatus.ISSUED, "BN010", "Le Van D");
        when(prescriptionRepository.findWithLockDetailsById(10L)).thenReturn(Optional.of(prescription));

        assertThatThrownBy(() -> service.markAsDispensed(10L, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_INVALID_STATUS)
                );

        verify(inventoryService, never()).dispenseFIFO(anyLong(), anyInt(), anyLong(), anyLong());
        verifyNoInteractions(inventoryService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void markAsDispensedKeepsPrescriptionPaidWhenStockDeductionFails() {
        Prescription prescription = prescription(11L, "RX-11", PrescriptionStatus.PAID, "BN011", "Pham Van E");
        addItem(prescription, 502L, 6);
        User dispenser = User.builder().id(99L).build();
        when(prescriptionRepository.findWithLockDetailsById(11L)).thenReturn(Optional.of(prescription));
        when(userRepository.findById(99L)).thenReturn(Optional.of(dispenser));
        doThrow(new ApiException(ErrorCode.INVENTORY_INSUFFICIENT_STOCK, "Không đủ tồn kho"))
                .when(inventoryService).validateDispenseAvailability(502L, 6);

        assertThatThrownBy(() -> service.markAsDispensed(11L, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_INSUFFICIENT_STOCK)
                );

        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.PAID);
        verify(inventoryService, never()).dispenseFIFO(anyLong(), anyInt(), anyLong(), anyLong());
        verify(prescriptionRepository, never()).save(any(Prescription.class));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void markAsDispensedSkipsRefundedPrescriptionItems() {
        Prescription prescription = prescription(12L, "RX-12", PrescriptionStatus.PAID, "BN012", "Vu Van F");
        PrescriptionItem refunded = addItem(prescription, 501L, 4);
        PrescriptionItem active = addItem(prescription, 502L, 2);
        refunded.setStatus(PrescriptionItemStatus.REFUNDED);
        active.setStatus(PrescriptionItemStatus.PAID);
        User dispenser = User.builder().id(99L).build();
        when(prescriptionRepository.findWithLockDetailsById(12L)).thenReturn(Optional.of(prescription));
        when(userRepository.findById(99L)).thenReturn(Optional.of(dispenser));
        when(prescriptionRepository.save(prescription)).thenReturn(prescription);

        PrescriptionResponse response = service.markAsDispensed(12L, 99L);

        assertThat(response.getStatus()).isEqualTo(PrescriptionStatus.DISPENSED);
        assertThat(refunded.getStatus()).isEqualTo(PrescriptionItemStatus.REFUNDED);
        assertThat(active.getStatus()).isEqualTo(PrescriptionItemStatus.DISPENSED);
        verify(inventoryService, never()).validateDispenseAvailability(501L, 4);
        verify(inventoryService, never()).dispenseFIFO(eq(501L), anyInt(), anyLong(), anyLong());
        verify(inventoryService).validateDispenseAvailability(502L, 2);
        verify(inventoryService).dispenseFIFO(502L, 2, 12L, 99L);
    }

    @Test
    void markAsDispensedRejectsPrescriptionWithOnlyRefundedItems() {
        Prescription prescription = prescription(13L, "RX-13", PrescriptionStatus.PAID, "BN013", "Vu Van G");
        PrescriptionItem refunded = addItem(prescription, 501L, 4);
        refunded.setStatus(PrescriptionItemStatus.REFUNDED);
        User dispenser = User.builder().id(99L).build();
        when(prescriptionRepository.findWithLockDetailsById(13L)).thenReturn(Optional.of(prescription));
        when(userRepository.findById(99L)).thenReturn(Optional.of(dispenser));

        assertThatThrownBy(() -> service.markAsDispensed(13L, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_INVALID_STATUS)
                );

        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.PAID);
        verify(inventoryService, never()).dispenseFIFO(anyLong(), anyInt(), anyLong(), anyLong());
        verify(prescriptionRepository, never()).save(any(Prescription.class));
        verifyNoInteractions(auditLogService);
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

    @Test
    void shouldCancelIssuedPrescriptionWhenDoctorOwnsEncounter() {
        Prescription prescription = prescription(5L, "RX-5", PrescriptionStatus.ISSUED, "BN005", "Pham Van D");
        when(prescriptionRepository.findById(5L)).thenReturn(java.util.Optional.of(prescription));
        when(userRepository.findById(prescription.getDoctorUser().getId())).thenReturn(java.util.Optional.of(prescription.getDoctorUser()));
        when(prescriptionRepository.save(prescription)).thenReturn(prescription);

        var response = service.cancel(5L, prescription.getDoctorUser().getId());

        assertThat(response.getStatus()).isEqualTo(PrescriptionStatus.CANCELLED);
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.CANCELLED);
    }

    @Test
    void shouldRejectCancelWhenPrescriptionIsPaid() {
        Prescription prescription = prescription(6L, "RX-6", PrescriptionStatus.PAID, "BN006", "Do Van E");
        when(prescriptionRepository.findById(6L)).thenReturn(java.util.Optional.of(prescription));
        when(userRepository.findById(prescription.getDoctorUser().getId())).thenReturn(java.util.Optional.of(prescription.getDoctorUser()));

        assertThatThrownBy(() -> service.cancel(6L, prescription.getDoctorUser().getId()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_INVALID_STATUS)
                );
    }

    @Test
    void shouldRejectCancelWhenPrescriptionIsDispensed() {
        Prescription prescription = prescription(7L, "RX-7", PrescriptionStatus.DISPENSED, "BN007", "Hoang Van F");
        when(prescriptionRepository.findById(7L)).thenReturn(java.util.Optional.of(prescription));
        when(userRepository.findById(prescription.getDoctorUser().getId())).thenReturn(java.util.Optional.of(prescription.getDoctorUser()));

        assertThatThrownBy(() -> service.cancel(7L, prescription.getDoctorUser().getId()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_INVALID_STATUS)
                );
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

    private PrescriptionItem addItem(Prescription prescription, Long medicationId, int quantity) {
        Medication medication = Medication.builder()
                .id(medicationId)
                .code("MED-" + medicationId)
                .name("Thuoc " + medicationId)
                .unit("vien")
                .build();

        PrescriptionItem item = PrescriptionItem.builder()
                .id(1000L + medicationId + prescription.getItems().size())
                .prescription(prescription)
                .medication(medication)
                .medicationCodeSnapshot(medication.getCode())
                .medicationNameSnapshot(medication.getName())
                .unitSnapshot(medication.getUnit())
                .quantity(quantity)
                .build();
        prescription.getItems().add(item);
        return item;
    }
}
