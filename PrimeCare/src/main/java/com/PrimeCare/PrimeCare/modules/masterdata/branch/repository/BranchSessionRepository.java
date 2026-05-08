package com.PrimeCare.PrimeCare.modules.masterdata.branch.repository;

import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.BranchSession;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchSessionRepository extends JpaRepository<BranchSession, Long> {
    Optional<BranchSession> findByBranch_IdAndSessionAndStatus(Long branchId, BranchSessionType session, String status);

    List<BranchSession> findByBranch_IdAndStatusOrderBySessionAsc(Long branchId, String status);
}
