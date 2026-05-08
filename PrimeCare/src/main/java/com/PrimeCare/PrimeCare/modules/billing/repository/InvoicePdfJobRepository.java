package com.PrimeCare.PrimeCare.modules.billing.repository;

import com.PrimeCare.PrimeCare.modules.billing.entity.InvoicePdfJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoicePdfJobRepository extends JpaRepository<InvoicePdfJob, Long> {
}