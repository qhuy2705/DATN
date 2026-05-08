package com.PrimeCare.PrimeCare.modules.cms.faq.repository;

import com.PrimeCare.PrimeCare.modules.cms.faq.entity.Faq;
import com.PrimeCare.PrimeCare.shared.enums.FaqStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    List<Faq> findTop12ByStatusOrderByCreatedAtDesc(FaqStatus status);
}
