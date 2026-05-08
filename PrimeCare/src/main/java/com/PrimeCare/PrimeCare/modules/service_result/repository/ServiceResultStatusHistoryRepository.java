package com.PrimeCare.PrimeCare.modules.service_result.repository;

import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResultStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceResultStatusHistoryRepository extends JpaRepository<ServiceResultStatusHistory, Long> {
    List<ServiceResultStatusHistory> findByServiceResult_IdOrderByChangedAtDesc(Long serviceResultId);
}
