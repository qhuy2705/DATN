package com.PrimeCare.PrimeCare.modules.publiccontact.repository;

import com.PrimeCare.PrimeCare.modules.publiccontact.entity.PublicContactSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicContactSubmissionRepository extends JpaRepository<PublicContactSubmission, Long> {
    Page<PublicContactSubmission> findByStatus(String status, Pageable pageable);
}
