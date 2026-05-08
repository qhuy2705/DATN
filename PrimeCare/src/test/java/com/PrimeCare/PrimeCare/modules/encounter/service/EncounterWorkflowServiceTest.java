package com.PrimeCare.PrimeCare.modules.encounter.service;

import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncounterWorkflowServiceTest {

    @Mock
    private ServiceOrderRepository serviceOrderRepository;
    @Mock
    private PrescriptionRepository prescriptionRepository;

    @InjectMocks
    private EncounterWorkflowService service;

    @Test
    void pendingPaymentOrderMovesEncounterToWaitingPayment() {
        Encounter encounter = encounter();
        when(serviceOrderRepository.findByEncounter_IdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(order(ServiceOrderStatus.PENDING_PAYMENT, ServiceOrderItemStatus.WAITING_EXECUTION)));
        when(prescriptionRepository.countByEncounter_IdAndStatus(1L, PrescriptionStatus.ISSUED)).thenReturn(0L);

        var state = service.getWorkflowState(encounter);

        assertThat(state.hasPendingPayment()).isTrue();
        assertThat(service.resolveStatus(encounter, state)).isEqualTo(EncounterStatus.WAITING_PAYMENT);
    }

    @Test
    void paidWaitingResultOrderMovesEncounterToWaitingResults() {
        Encounter encounter = encounter();
        when(serviceOrderRepository.findByEncounter_IdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(order(ServiceOrderStatus.IN_PROGRESS, ServiceOrderItemStatus.IN_PROGRESS)));
        when(prescriptionRepository.countByEncounter_IdAndStatus(1L, PrescriptionStatus.ISSUED)).thenReturn(0L);

        var state = service.getWorkflowState(encounter);

        assertThat(state.hasWaitingResults()).isTrue();
        assertThat(service.resolveStatus(encounter, state)).isEqualTo(EncounterStatus.WAITING_RESULTS);
    }

    @Test
    void allResultsDoneMovesEncounterToReadyForConclusion() {
        Encounter encounter = encounter();
        when(serviceOrderRepository.findByEncounter_IdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(order(ServiceOrderStatus.COMPLETED, ServiceOrderItemStatus.DONE)));
        when(prescriptionRepository.countByEncounter_IdAndStatus(1L, PrescriptionStatus.ISSUED)).thenReturn(0L);

        var state = service.getWorkflowState(encounter);

        assertThat(state.readyForConclusion()).isTrue();
        assertThat(service.resolveStatus(encounter, state)).isEqualTo(EncounterStatus.READY_FOR_CONCLUSION);
    }

    private Encounter encounter() {
        return Encounter.builder()
                .id(1L)
                .status(EncounterStatus.IN_PROGRESS)
                .build();
    }

    private ServiceOrder order(ServiceOrderStatus orderStatus, ServiceOrderItemStatus itemStatus) {
        ServiceOrder order = ServiceOrder.builder()
                .id(2L)
                .status(orderStatus)
                .items(new java.util.ArrayList<>())
                .build();
        order.getItems().add(ServiceOrderItem.builder().id(3L).status(itemStatus).build());
        return order;
    }
}
