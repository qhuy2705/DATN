package com.PrimeCare.PrimeCare.modules.encounter.validation;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates vital signs against medically accepted ranges.
 * Returns warnings (not hard errors) so doctors can still record unusual values with clinical judgment.
 */
public class VitalSignValidator {

    @Data
    @Builder
    public static class VitalSignWarning {
        private String field;
        private String fieldLabel;
        private Object value;
        private String message;
        private String severity; // WARNING, CRITICAL
    }

    public static List<VitalSignWarning> validate(
            Double heightCm, Double weightKg, Double temperatureC,
            Integer pulse, Integer systolicBp, Integer diastolicBp,
            Integer respiratoryRate, Integer spo2
    ) {
        List<VitalSignWarning> warnings = new ArrayList<>();

        if (temperatureC != null) {
            if (temperatureC < 35.0 || temperatureC > 42.0) {
                warnings.add(VitalSignWarning.builder()
                        .field("temperatureC").fieldLabel("Nhiệt độ")
                        .value(temperatureC)
                        .message("Nhiệt độ ngoài phạm vi bình thường (35.0 – 42.0 °C)")
                        .severity(temperatureC < 33.0 || temperatureC > 43.0 ? "CRITICAL" : "WARNING")
                        .build());
            }
        }

        if (pulse != null) {
            if (pulse < 40 || pulse > 200) {
                warnings.add(VitalSignWarning.builder()
                        .field("pulse").fieldLabel("Nhịp tim")
                        .value(pulse)
                        .message("Nhịp tim ngoài phạm vi bình thường (40 – 200 bpm)")
                        .severity(pulse < 30 || pulse > 220 ? "CRITICAL" : "WARNING")
                        .build());
            }
        }

        if (systolicBp != null) {
            if (systolicBp < 60 || systolicBp > 250) {
                warnings.add(VitalSignWarning.builder()
                        .field("systolicBp").fieldLabel("Huyết áp tâm thu")
                        .value(systolicBp)
                        .message("Huyết áp tâm thu ngoài phạm vi bình thường (60 – 250 mmHg)")
                        .severity(systolicBp < 50 || systolicBp > 280 ? "CRITICAL" : "WARNING")
                        .build());
            }
        }

        if (diastolicBp != null) {
            if (diastolicBp < 30 || diastolicBp > 150) {
                warnings.add(VitalSignWarning.builder()
                        .field("diastolicBp").fieldLabel("Huyết áp tâm trương")
                        .value(diastolicBp)
                        .message("Huyết áp tâm trương ngoài phạm vi bình thường (30 – 150 mmHg)")
                        .severity(diastolicBp < 20 || diastolicBp > 160 ? "CRITICAL" : "WARNING")
                        .build());
            }
        }

        if (systolicBp != null && diastolicBp != null) {
            if (systolicBp <= diastolicBp) {
                warnings.add(VitalSignWarning.builder()
                        .field("systolicBp").fieldLabel("Huyết áp")
                        .value(systolicBp + "/" + diastolicBp)
                        .message("Huyết áp tâm thu phải lớn hơn tâm trương")
                        .severity("CRITICAL")
                        .build());
            }
        }

        if (respiratoryRate != null) {
            if (respiratoryRate < 8 || respiratoryRate > 40) {
                warnings.add(VitalSignWarning.builder()
                        .field("respiratoryRate").fieldLabel("Nhịp thở")
                        .value(respiratoryRate)
                        .message("Nhịp thở ngoài phạm vi bình thường (8 – 40 lần/phút)")
                        .severity(respiratoryRate < 5 || respiratoryRate > 50 ? "CRITICAL" : "WARNING")
                        .build());
            }
        }

        if (spo2 != null) {
            if (spo2 < 50 || spo2 > 100) {
                warnings.add(VitalSignWarning.builder()
                        .field("spo2").fieldLabel("SpO2")
                        .value(spo2)
                        .message("SpO2 ngoài phạm vi bình thường (50 – 100%)")
                        .severity(spo2 < 50 ? "CRITICAL" : "WARNING")
                        .build());
            } else if (spo2 < 90) {
                warnings.add(VitalSignWarning.builder()
                        .field("spo2").fieldLabel("SpO2")
                        .value(spo2)
                        .message("SpO2 thấp, cần theo dõi sát (< 90%)")
                        .severity("WARNING")
                        .build());
            }
        }

        if (heightCm != null && (heightCm < 30 || heightCm > 250)) {
            warnings.add(VitalSignWarning.builder()
                    .field("heightCm").fieldLabel("Chiều cao")
                    .value(heightCm)
                    .message("Chiều cao ngoài phạm vi hợp lý (30 – 250 cm)")
                    .severity("WARNING")
                    .build());
        }

        if (weightKg != null && (weightKg < 0.5 || weightKg > 300)) {
            warnings.add(VitalSignWarning.builder()
                    .field("weightKg").fieldLabel("Cân nặng")
                    .value(weightKg)
                    .message("Cân nặng ngoài phạm vi hợp lý (0.5 – 300 kg)")
                    .severity("WARNING")
                    .build());
        }

        return warnings;
    }
}
