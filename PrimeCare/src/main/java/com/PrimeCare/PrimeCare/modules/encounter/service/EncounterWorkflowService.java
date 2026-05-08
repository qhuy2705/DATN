package com.PrimeCare.PrimeCare.modules.encounter.service;

import com.PrimeCare.PrimeCare.modules.encounter.dto.query.EncounterPrescriptionCount;
import com.PrimeCare.PrimeCare.modules.encounter.dto.query.EncounterServiceOrderSummary;
import com.PrimeCare.PrimeCare.modules.encounter.dto.record.EncounterWorkflowState;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderStatus;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EncounterWorkflowService {

    private final ServiceOrderRepository serviceOrderRepository;
    private final PrescriptionRepository prescriptionRepository;

    public EncounterWorkflowState getWorkflowState(Encounter encounter) {
        if (encounter == null || encounter.getId() == null) {
            return emptyState();
        }

        List<ServiceOrder> orders = serviceOrderRepository.findByEncounter_IdOrderByCreatedAtDesc(encounter.getId());

        int pendingPaymentOrderCount = 0;
        int activeServiceOrderCount = 0;
        int waitingResultItemCount = 0;
        int completedResultItemCount = 0;

        for (ServiceOrder order : orders) {
            if (order == null || order.getStatus() == ServiceOrderStatus.CANCELLED) {
                continue;
            }

            if (order.getStatus() != ServiceOrderStatus.COMPLETED) {
                activeServiceOrderCount++;
            }

            if (order.getStatus() == ServiceOrderStatus.PENDING_PAYMENT) {
                pendingPaymentOrderCount++;
            }

            for (ServiceOrderItem item : order.getItems()) {
                if (item.getStatus() == ServiceOrderItemStatus.DONE) {
                    completedResultItemCount++;
                    continue;
                }
                if (item.getStatus() == ServiceOrderItemStatus.WAITING_EXECUTION
                        || item.getStatus() == ServiceOrderItemStatus.IN_PROGRESS) {
                    waitingResultItemCount++;
                }
            }
        }

        long issuedPrescriptionCount = prescriptionRepository.countByEncounter_IdAndStatus(
                encounter.getId(),
                PrescriptionStatus.ISSUED
        );

        boolean terminal = isTerminal(encounter);
        boolean hasPendingPayment = pendingPaymentOrderCount > 0;
        boolean hasWaitingResults = waitingResultItemCount > 0;
        boolean readyForConclusion = !terminal
                && !hasPendingPayment
                && !hasWaitingResults
                && completedResultItemCount > 0;
        boolean canCreatePrescription = !terminal
                && !hasPendingPayment
                && !hasWaitingResults;
        boolean canComplete = canCreatePrescription
                && StringUtil.trimToNull(encounter.getFinalDiagnosis()) != null
                && StringUtil.trimToNull(encounter.getConclusion()) != null;

        return new EncounterWorkflowState(
                orders.size(),
                pendingPaymentOrderCount,
                activeServiceOrderCount,
                waitingResultItemCount,
                completedResultItemCount,
                (int) issuedPrescriptionCount,
                hasPendingPayment,
                hasWaitingResults,
                readyForConclusion,
                canCreatePrescription,
                canComplete
        );
    }

    public Map<Long, EncounterWorkflowState> getWorkflowStates(Collection<Encounter> encounters) {
        if (encounters == null || encounters.isEmpty()) {
            return Map.of();
        }

        Map<Long, Encounter> encounterById = encounters.stream()
                .filter(encounter -> encounter != null && encounter.getId() != null)
                .collect(Collectors.toMap(Encounter::getId, Function.identity(), (left, ignored) -> left));

        if (encounterById.isEmpty()) {
            return Map.of();
        }

        List<Long> encounterIds = List.copyOf(encounterById.keySet());
        Map<Long, EncounterServiceOrderSummary> serviceOrderSummaryByEncounterId = serviceOrderRepository
                .summarizeWorkflowByEncounterIds(encounterIds)
                .stream()
                .collect(Collectors.toMap(EncounterServiceOrderSummary::getEncounterId, Function.identity()));

        Map<Long, Long> issuedPrescriptionCountByEncounterId = prescriptionRepository
                .countByEncounterIdsAndStatus(encounterIds, PrescriptionStatus.ISSUED)
                .stream()
                .collect(Collectors.toMap(EncounterPrescriptionCount::getEncounterId, EncounterPrescriptionCount::getCount));

        return encounterById.values().stream()
                .collect(Collectors.toMap(
                        Encounter::getId,
                        encounter -> buildWorkflowState(
                                encounter,
                                serviceOrderSummaryByEncounterId.get(encounter.getId()),
                                issuedPrescriptionCountByEncounterId.getOrDefault(encounter.getId(), 0L)
                        )
                ));
    }

    public EncounterStatus refreshStatus(Encounter encounter) {
        EncounterStatus nextStatus = resolveStatus(encounter);
        if (encounter != null && nextStatus != null) {
            encounter.setStatus(nextStatus);
        }
        return nextStatus;
    }

    public EncounterStatus resolveStatus(Encounter encounter) {
        return resolveStatus(encounter, getWorkflowState(encounter));
    }

    public EncounterStatus resolveStatus(Encounter encounter, EncounterWorkflowState state) {
        if (encounter == null) {
            return null;
        }
        if (encounter.getStatus() == EncounterStatus.COMPLETED
                || encounter.getStatus() == EncounterStatus.CANCELLED) {
            return encounter.getStatus();
        }
        if (state.hasPendingPayment()) {
            return EncounterStatus.WAITING_PAYMENT;
        }
        if (state.hasWaitingResults()) {
            return EncounterStatus.WAITING_RESULTS;
        }
        if (state.completedResultItemCount() > 0) {
            return EncounterStatus.READY_FOR_CONCLUSION;
        }
        if (encounter.getStatus() == EncounterStatus.REOPENED) {
            return EncounterStatus.REOPENED;
        }
        return EncounterStatus.IN_PROGRESS;
    }

    private EncounterWorkflowState emptyState() {
        return new EncounterWorkflowState(
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                false
        );
    }

    private EncounterWorkflowState buildWorkflowState(
            Encounter encounter,
            EncounterServiceOrderSummary serviceOrderSummary,
            long issuedPrescriptionCount
    ) {
        int serviceOrderCount = toInt(serviceOrderSummary != null ? serviceOrderSummary.getServiceOrderCount() : 0);
        int pendingPaymentOrderCount = toInt(serviceOrderSummary != null ? serviceOrderSummary.getPendingPaymentOrderCount() : 0);
        int activeServiceOrderCount = toInt(serviceOrderSummary != null ? serviceOrderSummary.getActiveServiceOrderCount() : 0);
        int waitingResultItemCount = toInt(serviceOrderSummary != null ? serviceOrderSummary.getWaitingResultItemCount() : 0);
        int completedResultItemCount = toInt(serviceOrderSummary != null ? serviceOrderSummary.getCompletedResultItemCount() : 0);

        boolean terminal = isTerminal(encounter);
        boolean hasPendingPayment = pendingPaymentOrderCount > 0;
        boolean hasWaitingResults = waitingResultItemCount > 0;
        boolean readyForConclusion = !terminal
                && !hasPendingPayment
                && !hasWaitingResults
                && completedResultItemCount > 0;
        boolean canCreatePrescription = !terminal
                && !hasPendingPayment
                && !hasWaitingResults;
        boolean canComplete = canCreatePrescription
                && StringUtil.trimToNull(encounter.getFinalDiagnosis()) != null
                && StringUtil.trimToNull(encounter.getConclusion()) != null;

        return new EncounterWorkflowState(
                serviceOrderCount,
                pendingPaymentOrderCount,
                activeServiceOrderCount,
                waitingResultItemCount,
                completedResultItemCount,
                toInt(issuedPrescriptionCount),
                hasPendingPayment,
                hasWaitingResults,
                readyForConclusion,
                canCreatePrescription,
                canComplete
        );
    }

    private int toInt(long value) {
        return Math.toIntExact(value);
    }

    private boolean isTerminal(Encounter encounter) {
        return encounter.getStatus() == EncounterStatus.COMPLETED
                || encounter.getStatus() == EncounterStatus.CANCELLED;
    }
}
