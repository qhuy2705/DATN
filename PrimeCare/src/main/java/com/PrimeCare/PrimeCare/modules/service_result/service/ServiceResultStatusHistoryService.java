package com.PrimeCare.PrimeCare.modules.service_result.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResultStatusHistory;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultStatusHistoryRepository;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ServiceResultStatusHistoryService {

    private final ServiceResultStatusHistoryRepository repository;

    @Transactional
    public void record(ServiceResult result, ServiceResultStatus fromStatus, ServiceResultStatus toStatus, User changedBy, String note) {
        if (result == null || toStatus == null) return;
        if (fromStatus == toStatus) return;
        repository.save(ServiceResultStatusHistory.builder()
                .serviceResult(result)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .changedAt(LocalDateTime.now())
                .note(note)
                .build());
    }
}
