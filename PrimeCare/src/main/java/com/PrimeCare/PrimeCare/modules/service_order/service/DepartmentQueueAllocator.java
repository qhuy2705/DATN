package com.PrimeCare.PrimeCare.modules.service_order.service;

import com.PrimeCare.PrimeCare.modules.service_order.entity.DepartmentQueueCounter;
import com.PrimeCare.PrimeCare.modules.service_order.repository.DepartmentQueueCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DepartmentQueueAllocator {

    private final DepartmentQueueCounterRepository counterRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public int allocateNext(String departmentCode, LocalDate queueDate) {
        return counterRepository.findWithLockByDepartmentCodeAndQueueDate(departmentCode, queueDate)
                                .map(counter -> {
                                    int allocated = counter.getNextQueueNo();
                                    counter.setNextQueueNo(allocated + 1);
                                    return allocated;
                                })
                                .orElseGet(() -> createCounterAndAllocateFirst(departmentCode, queueDate));
    }

    private int createCounterAndAllocateFirst(String departmentCode, LocalDate queueDate) {
        try {
            counterRepository.saveAndFlush(
                    DepartmentQueueCounter.builder()
                                          .departmentCode(departmentCode)
                                          .queueDate(queueDate)
                                          .nextQueueNo(2)
                                          .build()
            );
            return 1;
        } catch (DataIntegrityViolationException ex) {
            DepartmentQueueCounter counter = counterRepository
                    .findWithLockByDepartmentCodeAndQueueDate(departmentCode, queueDate)
                    .orElseThrow(() -> ex);

            int allocated = counter.getNextQueueNo();
            counter.setNextQueueNo(allocated + 1);
            return allocated;
        }
    }
}