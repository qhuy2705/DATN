package com.PrimeCare.PrimeCare.modules.medication.repository;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MedicationBatchRepositoryConcurrencyContractTest {

    @Test
    void shouldLockAvailableBatchesWhenSelectingForDispense() throws Exception {
        String repositorySource = Files.readString(Path.of(
                "src/main/java/com/PrimeCare/PrimeCare/modules/medication/repository/MedicationBatchRepository.java"
        ));
        String batchSource = Files.readString(Path.of(
                "src/main/java/com/PrimeCare/PrimeCare/modules/medication/entity/MedicationBatch.java"
        ));

        assertThat(repositorySource)
                .as("Concurrent dispense must not select the same stock without a write lock")
                .contains("@Lock");
        assertThat(repositorySource + batchSource)
                .as("Dispense needs pessimistic locking or entity versioning to prevent oversell")
                .containsAnyOf("PESSIMISTIC_WRITE", "@Version");
    }
}
