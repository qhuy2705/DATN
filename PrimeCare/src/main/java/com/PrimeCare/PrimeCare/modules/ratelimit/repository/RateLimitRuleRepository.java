package com.PrimeCare.PrimeCare.modules.ratelimit.repository;

import com.PrimeCare.PrimeCare.modules.ratelimit.entity.RateLimitRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RateLimitRuleRepository extends JpaRepository<RateLimitRuleEntity, Long> {
    List<RateLimitRuleEntity> findAllByOrderByPriorityAscIdAsc();

    List<RateLimitRuleEntity> findByEnabledTrueOrderByPriorityAscIdAsc();

    boolean existsByCode(String code);

    boolean existsByHttpMethodAndPathPattern(String httpMethod, String pathPattern);
}
