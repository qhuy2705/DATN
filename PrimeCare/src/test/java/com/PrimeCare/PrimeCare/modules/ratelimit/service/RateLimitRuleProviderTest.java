package com.PrimeCare.PrimeCare.modules.ratelimit.service;

import com.PrimeCare.PrimeCare.modules.ratelimit.entity.RateLimitRuleEntity;
import com.PrimeCare.PrimeCare.modules.ratelimit.repository.RateLimitRuleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitRuleProviderTest {

    @Test
    void refreshCachesEnabledRulesAndMatchesByCurrentPrefixBehavior() {
        RateLimitRuleRepository repository = mock(RateLimitRuleRepository.class);
        RateLimitRuleEntity rule = rule(2L, "/api/cashier/service-orders/", "POST", "INVOICE_CREATE", 20, 60, 6, 20);
        when(repository.findByEnabledTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(rule));

        RateLimitRuleProvider provider = new RateLimitRuleProvider(repository);
        provider.refreshCache();

        Optional<?> match = provider.findMatching("POST", "/api/cashier/service-orders/123/invoice");

        assertThat(match).isPresent();
        assertThat(provider.findMatching("GET", "/api/cashier/service-orders/123/invoice")).isEmpty();
    }

    private RateLimitRuleEntity rule(
            Long id,
            String pathPattern,
            String httpMethod,
            String eventType,
            long limitCount,
            long windowSeconds,
            long bucketSeconds,
            int priority
    ) {
        return RateLimitRuleEntity.builder()
                .id(id)
                .code(eventType + "_" + id)
                .name(eventType)
                .pathPattern(pathPattern)
                .httpMethod(httpMethod)
                .eventType(eventType)
                .limitCount(limitCount)
                .windowSeconds(windowSeconds)
                .bucketSeconds(bucketSeconds)
                .enabled(true)
                .priority(priority)
                .defaultLimitCount(limitCount)
                .defaultWindowSeconds(windowSeconds)
                .defaultBucketSeconds(bucketSeconds)
                .defaultEnabled(true)
                .build();
    }
}
