package com.PrimeCare.PrimeCare.modules.ratelimit.service;

import com.PrimeCare.PrimeCare.modules.ratelimit.config.RateLimitRule;
import com.PrimeCare.PrimeCare.modules.ratelimit.entity.RateLimitRuleEntity;
import com.PrimeCare.PrimeCare.modules.ratelimit.repository.RateLimitRuleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RateLimitRuleProvider {

    private final RateLimitRuleRepository rateLimitRuleRepository;

    private volatile List<RateLimitRule> cachedRules = List.of();

    @PostConstruct
    public void refreshOnStartup() {
        refreshCache();
    }

    public void refreshCache() {
        cachedRules = rateLimitRuleRepository.findByEnabledTrueOrderByPriorityAscIdAsc()
                .stream()
                .map(this::toRuntimeRule)
                .toList();
    }

    public Optional<RateLimitRule> findMatching(String method, String path) {
        for (RateLimitRule rule : cachedRules) {
            if (rule.matches(method, path)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    public List<RateLimitRule> currentRules() {
        return cachedRules;
    }

    private RateLimitRule toRuntimeRule(RateLimitRuleEntity entity) {
        return new RateLimitRule(
                entity.getId(),
                entity.getPathPattern(),
                entity.getHttpMethod(),
                entity.getEventType(),
                entity.getLimitCount(),
                entity.getWindowSeconds(),
                entity.getBucketSeconds(),
                entity.getPriority()
        );
    }
}
