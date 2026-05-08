package com.PrimeCare.PrimeCare.modules.billing.repository;

import com.PrimeCare.PrimeCare.modules.billing.entity.RefundRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, Long> {
    List<RefundRecord> findByInvoice_IdOrderByRefundedAtDesc(Long invoiceId);
}
