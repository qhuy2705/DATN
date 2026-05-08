package com.PrimeCare.PrimeCare.modules.pharmacy.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.medication.entity.InventoryTransaction;
import com.PrimeCare.PrimeCare.modules.medication.entity.Medication;
import com.PrimeCare.PrimeCare.modules.medication.entity.MedicationBatch;
import com.PrimeCare.PrimeCare.modules.medication.repository.InventoryTransactionRepository;
import com.PrimeCare.PrimeCare.modules.medication.repository.MedicationBatchRepository;
import com.PrimeCare.PrimeCare.modules.medication.repository.MedicationRepository;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private MedicationBatchRepository batchRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private MedicationRepository medicationRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private InventoryService service;

    @Test
    void dispenseFifoDeductsOldestExpiringBatchesFirst() {
        Medication medication = Medication.builder().id(1L).name("Thuoc A").build();
        MedicationBatch first = batch(11L, medication, 3, LocalDate.now().plusDays(10));
        MedicationBatch second = batch(12L, medication, 5, LocalDate.now().plusDays(20));
        when(batchRepository.findAvailableBatchesFIFO(1L)).thenReturn(List.of(first, second));
        when(userRepository.findById(99L)).thenReturn(Optional.of(User.builder().id(99L).build()));

        int dispensed = service.dispenseFIFO(1L, 5, 7L, 99L);

        assertThat(dispensed).isEqualTo(5);
        assertThat(first.getQuantityInStock()).isZero();
        assertThat(second.getQuantityInStock()).isEqualTo(3);
        verify(batchRepository).save(first);
        verify(batchRepository).save(second);
        verify(transactionRepository, times(2)).save(any(InventoryTransaction.class));
    }

    @Test
    void dispenseFifoFailsWhenStockIsInsufficient() {
        Medication medication = Medication.builder().id(1L).name("Thuoc A").build();
        MedicationBatch onlyBatch = batch(11L, medication, 2, LocalDate.now().plusDays(10));
        when(batchRepository.findAvailableBatchesFIFO(1L)).thenReturn(List.of(onlyBatch));
        when(userRepository.findById(99L)).thenReturn(Optional.of(User.builder().id(99L).build()));

        assertThatThrownBy(() -> service.dispenseFIFO(1L, 5, 7L, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_INSUFFICIENT_STOCK)
                );
    }

    private MedicationBatch batch(Long id, Medication medication, int quantity, LocalDate expiryDate) {
        return MedicationBatch.builder()
                .id(id)
                .medication(medication)
                .batchNumber("B-" + id)
                .quantityInStock(quantity)
                .expiryDate(expiryDate)
                .build();
    }
}
