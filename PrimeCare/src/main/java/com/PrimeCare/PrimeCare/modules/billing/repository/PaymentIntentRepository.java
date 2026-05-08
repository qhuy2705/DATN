package com.PrimeCare.PrimeCare.modules.billing.repository;

import com.PrimeCare.PrimeCare.modules.billing.entity.PaymentIntent;
import com.PrimeCare.PrimeCare.shared.enums.PaymentIntentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, Long> {

    @EntityGraph(attributePaths = {"invoice", "invoice.serviceOrder", "invoice.serviceOrder.encounter", "invoice.serviceOrder.encounter.doctor", "invoice.serviceOrder.branch"})
    Optional<PaymentIntent> findByInvoice_Id(Long invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"invoice", "invoice.serviceOrder", "invoice.serviceOrder.encounter", "invoice.serviceOrder.encounter.doctor", "invoice.serviceOrder.branch"})
    Optional<PaymentIntent> findWithLockByInvoice_Id(Long invoiceId);

    Optional<PaymentIntent> findByPaymentReference(String paymentReference);

    @EntityGraph(attributePaths = {"invoice", "invoice.serviceOrder", "invoice.serviceOrder.encounter", "invoice.serviceOrder.encounter.doctor", "invoice.serviceOrder.branch"})
    List<PaymentIntent> findByExpectedAmountAndStatusIn(Long expectedAmount, Collection<PaymentIntentStatus> statuses);
}
