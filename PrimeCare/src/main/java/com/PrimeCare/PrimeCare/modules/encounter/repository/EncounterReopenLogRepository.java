package com.PrimeCare.PrimeCare.modules.encounter.repository;

import com.PrimeCare.PrimeCare.modules.encounter.entity.EncounterReopenLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EncounterReopenLogRepository extends JpaRepository<EncounterReopenLog, Long> {
    List<EncounterReopenLog> findByEncounter_IdOrderByReopenedAtDesc(Long encounterId);
}
