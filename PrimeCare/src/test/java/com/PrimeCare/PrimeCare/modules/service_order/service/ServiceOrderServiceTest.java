package com.PrimeCare.PrimeCare.modules.service_order.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.medical_service.repository.MedicalServiceRepository;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.dto.request.CreateServiceOrderRequest;
import com.PrimeCare.PrimeCare.modules.service_order.dto.request.ServiceOrderItemRequest;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceOrderServiceTest {

    @Mock
    private ServiceOrderRepository serviceOrderRepository;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private MedicalServiceRepository medicalServiceRepository;
    @Mock
    private ServiceResultRepository serviceResultRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private InternalNotificationService internalNotificationService;

    @InjectMocks
    private ServiceOrderService service;

    @Test
    void shouldRejectCreateWhenLockedEncounterAlreadyHasActiveServiceOrder() {
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").build();
        User doctorUser = User.builder()
                .id(9L)
                .role(UserRole.DOCTOR)
                .doctorProfile(doctor)
                .build();
        Encounter encounter = Encounter.builder()
                .id(1L)
                .doctor(doctor)
                .branch(Branch.builder().id(4L).nameVn("PrimeCare").build())
                .patientFullNameSnapshot("Patient Test")
                .status(EncounterStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();
        CreateServiceOrderRequest request = requestWithOneItem();

        when(userRepository.findById(9L)).thenReturn(Optional.of(doctorUser));
        when(encounterRepository.findWithLockById(1L)).thenReturn(Optional.of(encounter));
        when(serviceOrderRepository.existsByEncounter_IdAndStatusIn(
                eq(1L),
                org.mockito.ArgumentMatchers.<Collection<ServiceOrderStatus>>argThat(statuses ->
                        statuses.contains(ServiceOrderStatus.PENDING_PAYMENT)
                                && statuses.contains(ServiceOrderStatus.PAID)
                                && statuses.contains(ServiceOrderStatus.IN_PROGRESS)
                )
        )).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, 9L, request))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_ORDER_INVALID_STATUS)
                );

        InOrder inOrder = inOrder(userRepository, encounterRepository, serviceOrderRepository);
        inOrder.verify(userRepository).findById(9L);
        inOrder.verify(encounterRepository).findWithLockById(1L);
        inOrder.verify(serviceOrderRepository).existsByEncounter_IdAndStatusIn(eq(1L), any());
        verify(medicalServiceRepository, never()).findById(any());
    }

    private CreateServiceOrderRequest requestWithOneItem() {
        ServiceOrderItemRequest item = new ServiceOrderItemRequest();
        item.setMedicalServiceId(11L);
        item.setQuantity(1);
        CreateServiceOrderRequest request = new CreateServiceOrderRequest();
        request.setItems(List.of(item));
        return request;
    }
}
