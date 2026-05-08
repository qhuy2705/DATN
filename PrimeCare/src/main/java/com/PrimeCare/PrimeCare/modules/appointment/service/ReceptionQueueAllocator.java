package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.ReceptionQueueCounter;
import com.PrimeCare.PrimeCare.modules.appointment.repository.ReceptionQueueCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ReceptionQueueAllocator {

    private final ReceptionQueueCounterRepository counterRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public int allocateNext(Long branchId, LocalDate queueDate) {
        return counterRepository.findWithLockByBranchIdAndQueueDate(branchId, queueDate)
                                .map(counter -> {
                                    int allocated = counter.getNextQueueNo();
                                    counter.setNextQueueNo(allocated + 1);
                                    return allocated;
                                })
                                .orElseGet(() -> createCounterAndAllocateFirst(branchId, queueDate));
    }

    private int createCounterAndAllocateFirst(Long branchId, LocalDate queueDate) {
        try {
            counterRepository.saveAndFlush(
                    ReceptionQueueCounter.builder()
                                         .branchId(branchId)
                                         .queueDate(queueDate)
                                         .nextQueueNo(2)
                                         .build()
            );
            return 1;
        } catch (DataIntegrityViolationException ex) {
            ReceptionQueueCounter counter = counterRepository
                    .findWithLockByBranchIdAndQueueDate(branchId, queueDate)
                    .orElseThrow(() -> ex);

            int allocated = counter.getNextQueueNo();
            counter.setNextQueueNo(allocated + 1);
            return allocated;
        }
    }
}