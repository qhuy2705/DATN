package com.PrimeCare.PrimeCare.modules.billing.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.billing.entity.InvoiceStatusHistory;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceStatusHistoryRepository;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InvoiceStatusHistoryService {

    private final InvoiceStatusHistoryRepository repository;

    @Transactional
    public void record(Invoice invoice, PaymentStatus fromStatus, PaymentStatus toStatus, User changedBy, String note) {
        if (invoice == null || toStatus == null) return;
        if (fromStatus == toStatus) return;
        repository.save(InvoiceStatusHistory.builder()
                .invoice(invoice)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .changedAt(LocalDateTime.now())
                .note(note)
                .build());
    }
}
