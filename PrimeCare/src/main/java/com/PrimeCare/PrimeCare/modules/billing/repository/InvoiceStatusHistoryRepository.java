package com.PrimeCare.PrimeCare.modules.billing.repository;

import com.PrimeCare.PrimeCare.modules.billing.entity.InvoiceStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceStatusHistoryRepository extends JpaRepository<InvoiceStatusHistory, Long> {
    List<InvoiceStatusHistory> findByInvoice_IdOrderByChangedAtDesc(Long invoiceId);
}
