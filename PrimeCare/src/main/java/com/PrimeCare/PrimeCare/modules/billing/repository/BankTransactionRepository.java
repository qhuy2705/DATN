package com.PrimeCare.PrimeCare.modules.billing.repository;

import com.PrimeCare.PrimeCare.modules.billing.entity.BankTransaction;
import com.PrimeCare.PrimeCare.shared.enums.BankTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    Optional<BankTransaction> findByTransactionRef(String transactionRef);

    boolean existsByMatchedInvoice_IdAndStatusIn(Long invoiceId, Collection<BankTransactionStatus> statuses);
}
