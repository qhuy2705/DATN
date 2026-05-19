package com.PrimeCare.PrimeCare.modules.triage;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriageKnowledgeBaseService {

    private static final String RED_FLAGS_FILE = "triage/red-flags.vi.json";
    private static final String SYMPTOMS_FILE = "triage/symptoms.vi.json";
    private static final String RISK_MODIFIERS_FILE = "triage/risk-modifiers.vi.json";
    private static final String MEDICATION_RISKS_FILE = "triage/medication-risks.vi.json";
    private static final String SEVERITY_TERMS_FILE = "triage/severity-terms.vi.json";
    private static final String RULES_FILE = "triage/triage-rules.json";

    private final ObjectMapper objectMapper;

    private KnowledgeBaseSnapshot snapshot = KnowledgeBaseSnapshot.empty();

    @PostConstruct
    void loadOnStartup() {
        reload();
    }

    public synchronized void reload() {
        DefinitionFile redFlags = loadDefinitionFile(RED_FLAGS_FILE, true);
        DefinitionFile symptoms = loadDefinitionFile(SYMPTOMS_FILE, false);
        DefinitionFile riskModifiers = loadDefinitionFile(RISK_MODIFIERS_FILE, false);
        DefinitionFile medicationRisks = loadDefinitionFile(MEDICATION_RISKS_FILE, false);
        DefinitionFile severityTerms = loadDefinitionFile(SEVERITY_TERMS_FILE, false);
        RuleFile rules = loadRuleFile(RULES_FILE, true);

        String knowledgeVersion = joinVersions(
                redFlags.getVersion(),
                symptoms.getVersion(),
                riskModifiers.getVersion(),
                medicationRisks.getVersion(),
                severityTerms.getVersion()
        );
        snapshot = KnowledgeBaseSnapshot.builder()
                .redFlags(safeItems(redFlags))
                .symptoms(safeItems(symptoms))
                .riskModifiers(safeItems(riskModifiers))
                .medicationRisks(safeItems(medicationRisks))
                .severityTerms(safeItems(severityTerms))
                .rules(rules.getItems() != null ? rules.getItems() : List.of())
                .knowledgeBaseVersion(knowledgeVersion)
                .rulesetVersion(rules.getVersion())
                .build();
        log.info("Loaded triage knowledge base version={} ruleset={} redFlags={} symptoms={} riskModifiers={} medicationRisks={} severityTerms={} rules={}",
                snapshot.getKnowledgeBaseVersion(),
                snapshot.getRulesetVersion(),
                snapshot.getRedFlags().size(),
                snapshot.getSymptoms().size(),
                snapshot.getRiskModifiers().size(),
                snapshot.getMedicationRisks().size(),
                snapshot.getSeverityTerms().size(),
                snapshot.getRules().size());
    }

    public List<TriageDefinition> redFlags() {
        return snapshot.getRedFlags();
    }

    public List<TriageDefinition> symptoms() {
        return snapshot.getSymptoms();
    }

    public List<TriageDefinition> riskModifiers() {
        return snapshot.getRiskModifiers();
    }

    public List<TriageDefinition> medicationRisks() {
        return snapshot.getMedicationRisks();
    }

    public List<TriageDefinition> severityTerms() {
        return snapshot.getSeverityTerms();
    }

    public List<TriageRuleDefinition> rules() {
        return snapshot.getRules();
    }

    public String knowledgeBaseVersion() {
        return snapshot.getKnowledgeBaseVersion();
    }

    public String rulesetVersion() {
        return snapshot.getRulesetVersion();
    }

    public Optional<TriageDefinition> findDefinition(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return allDefinitions().stream()
                .filter(item -> code.equals(item.getCode()))
                .findFirst();
    }

    private List<TriageDefinition> allDefinitions() {
        List<TriageDefinition> definitions = new ArrayList<>();
        definitions.addAll(redFlags());
        definitions.addAll(symptoms());
        definitions.addAll(riskModifiers());
        definitions.addAll(medicationRisks());
        definitions.addAll(severityTerms());
        return definitions;
    }

    private DefinitionFile loadDefinitionFile(String path, boolean required) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            DefinitionFile file = objectMapper.readValue(inputStream, DefinitionFile.class);
            if (file.getItems() == null) {
                file.setItems(List.of());
            }
            return file;
        } catch (Exception ex) {
            if (required) {
                throw new IllegalStateException("Cannot load required triage knowledge file " + path, ex);
            }
            log.warn("Cannot load optional triage knowledge file {}: {}", path, ex.getMessage());
            return new DefinitionFile();
        }
    }

    private RuleFile loadRuleFile(String path, boolean required) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            RuleFile file = objectMapper.readValue(inputStream, RuleFile.class);
            if (file.getItems() == null) {
                file.setItems(List.of());
            }
            return file;
        } catch (Exception ex) {
            if (required) {
                throw new IllegalStateException("Cannot load required triage rules file " + path, ex);
            }
            log.warn("Cannot load optional triage rules file {}: {}", path, ex.getMessage());
            return new RuleFile();
        }
    }

    private List<TriageDefinition> safeItems(DefinitionFile file) {
        return file != null && file.getItems() != null ? file.getItems() : List.of();
    }

    private String joinVersions(String... versions) {
        List<String> parts = new ArrayList<>();
        for (String version : versions) {
            if (version != null && !version.isBlank() && !parts.contains(version)) {
                parts.add(version);
            }
        }
        return parts.isEmpty() ? "unknown" : String.join("+", parts);
    }

    @Data
    public static class DefinitionFile {
        private String version = "unknown";
        private List<TriageDefinition> items = List.of();
    }

    @Data
    public static class RuleFile {
        private String version = "unknown";
        private List<TriageRuleDefinition> items = List.of();
    }

    @Data
    @lombok.Builder
    private static class KnowledgeBaseSnapshot {
        private String knowledgeBaseVersion;
        private String rulesetVersion;
        private List<TriageDefinition> redFlags;
        private List<TriageDefinition> symptoms;
        private List<TriageDefinition> riskModifiers;
        private List<TriageDefinition> medicationRisks;
        private List<TriageDefinition> severityTerms;
        private List<TriageRuleDefinition> rules;

        static KnowledgeBaseSnapshot empty() {
            return KnowledgeBaseSnapshot.builder()
                    .knowledgeBaseVersion("unknown")
                    .rulesetVersion("unknown")
                    .redFlags(List.of())
                    .symptoms(List.of())
                    .riskModifiers(List.of())
                    .medicationRisks(List.of())
                    .severityTerms(List.of())
                    .rules(List.of())
                    .build();
        }
    }
}
