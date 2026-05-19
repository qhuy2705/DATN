package com.PrimeCare.PrimeCare.modules.service_result.repository;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceResultRepositoryContractTest {

    @Test
    void shouldCountOnlyVerifiedPdfReadyResultsWhenCountingReadyByPatientId() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/PrimeCare/PrimeCare/modules/service_result/repository/ServiceResultRepository.java"
        ));

        assertThat(source).contains("countReadyByPatientId");
        assertThat(source).contains("sr.reportPdfPath is not null");
        assertThat(source).contains("trim(sr.reportPdfPath) <> ''");
        assertThat(source)
                .as("Patient portal ready-result count must not include DRAFT or COMPLETED results")
                .contains("VERIFIED");
    }

    @Test
    void shouldLockServiceOrderItemWhenSubmittingOrVerifyingResults() throws Exception {
        String repositorySource = Files.readString(Path.of(
                "src/main/java/com/PrimeCare/PrimeCare/modules/service_order/repository/ServiceOrderItemRepository.java"
        ));
        String serviceSource = Files.readString(Path.of(
                "src/main/java/com/PrimeCare/PrimeCare/modules/service_result/service/ServiceResultService.java"
        ));

        assertThat(repositorySource).contains("findWithLockById");
        assertThat(repositorySource).contains("@Lock(LockModeType.PESSIMISTIC_WRITE)");
        assertThat(serviceSource)
                .as("submit and verify must serialize concurrent updates for the same service-order item")
                .contains("itemRepository.findWithLockById(itemId)");
    }
}
