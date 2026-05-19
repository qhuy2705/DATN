package com.PrimeCare.PrimeCare.modules.billing.repository;

import com.PrimeCare.PrimeCare.modules.billing.entity.RefundRecordItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRecordItemRepository extends JpaRepository<RefundRecordItem, Long> {
}
