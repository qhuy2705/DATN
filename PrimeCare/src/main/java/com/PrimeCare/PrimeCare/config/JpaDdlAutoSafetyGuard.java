package com.PrimeCare.PrimeCare.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JpaDdlAutoSafetyGuard {

    private static final Set<String> DANGEROUS_DDL_AUTO_VALUES = Set.of("create", "create-drop");

    private final Environment environment;

    @Value("${spring.jpa.hibernate.ddl-auto:}")
    private String ddlAuto;

    @PostConstruct
    void validateDdlAuto() {
        String normalized = ddlAuto == null ? "" : ddlAuto.trim().toLowerCase(Locale.ROOT);
        if (!DANGEROUS_DDL_AUTO_VALUES.contains(normalized)) {
            return;
        }

        boolean testProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (!testProfile) {
            throw new IllegalStateException(
                    "Refusing to start with unsafe spring.jpa.hibernate.ddl-auto=" + ddlAuto + " outside test profile"
            );
        }
    }
}
