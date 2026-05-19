package com.PrimeCare.PrimeCare.modules.triage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TriagePriorityNormalizerTest {

    @Test
    void normalizesChronicConditionOthersSafely() {
        String longText = "A".repeat(120);

        List<String> values = TriagePriorityNormalizer.normalizeChronicConditionOthers(List.of(
                " Suy thận mạn ",
                "suy thận mạn",
                "",
                longText,
                "Đang dùng thuốc chống đông",
                "Vừa phẫu thuật",
                "Bệnh tự miễn",
                "Extra"
        ));

        assertThat(values).hasSize(5);
        assertThat(values.get(0)).isEqualTo("Suy thận mạn");
        assertThat(values.get(1)).hasSize(80);
        assertThat(values).doesNotContain("");
    }
}
