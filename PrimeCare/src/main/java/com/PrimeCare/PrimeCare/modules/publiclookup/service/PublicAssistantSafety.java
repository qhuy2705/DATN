package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import java.util.List;
import java.util.Optional;

import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.searchable;

final class PublicAssistantSafety {

    static final String PROVIDER_DISPLAY_NAME = "PrimeCare AI";
    static final String MEDICATION_BLOCK_MESSAGE = "Tôi không thể tư vấn thuốc, liều dùng, kê đơn hoặc phác đồ điều trị. Để đảm bảo an toàn, bạn nên đặt lịch khám để bác sĩ đánh giá trực tiếp và đưa ra hướng xử lý phù hợp.";
    static final String DIAGNOSIS_BLOCK_MESSAGE = "Tôi không thể kết luận hoặc chẩn đoán bệnh thay bác sĩ. Tôi chỉ có thể gợi ý chuyên khoa phù hợp và hỗ trợ bạn đặt lịch khám để được bác sĩ kiểm tra trực tiếp.";
    static final String EMERGENCY_BLOCK_MESSAGE = "Triệu chứng bạn mô tả có thể cần được xử lý khẩn cấp. Bạn nên liên hệ cơ sở y tế gần nhất hoặc gọi cấp cứu ngay. PrimeCare AI không thay thế bác sĩ trong các tình huống khẩn cấp.";

    private static final List<String> EMERGENCY_PHRASES = List.of(
            "dau nguc du doi",
            "dau nguc keo dai",
            "dau nguc kho tho",
            "dau nguc va kho tho",
            "kho tho nang",
            "kho tho du doi",
            "ngat",
            "ngat xiu",
            "yeu nua nguoi",
            "liet nua nguoi",
            "yeu liet nua nguoi",
            "meo mieng",
            "noi kho",
            "co giat",
            "chay mau nhieu",
            "dau dau du doi dot ngot",
            "soc phan ve",
            "dau bung du doi kem non lien tuc",
            "tim tai",
            "bat tinh",
            "hon me"
    );

    private static final List<String> MEDICATION_PHRASES = List.of(
            "uong thuoc gi",
            "thuoc gi",
            "toi nen uong gi",
            "nen uong gi",
            "uong gi cho khoi",
            "co thuoc nao",
            "co thuoc nao khong",
            "co thuoc nao giam dau",
            "thuoc nao giam dau",
            "toi uong panadol duoc khong",
            "toi uong paracetamol duoc khong",
            "toi uong ibuprofen duoc khong",
            "toi uong aspirin duoc khong",
            "toi uong omeprazole duoc khong",
            "toi uong berberin duoc khong",
            "toi uong smecta duoc khong",
            "toi nen mua thuoc gi",
            "nen mua thuoc gi",
            "co nen dung thuoc",
            "cho toi don thuoc",
            "ke thuoc",
            "ke don",
            "ke don cho toi",
            "don thuoc",
            "lieu dung",
            "lieu dung the nao",
            "dung thuoc",
            "dung khang sinh",
            "khang sinh nao",
            "dung khang sinh nao",
            "paracetamol bao nhieu",
            "dieu tri the nao",
            "treatment plan",
            "dosage",
            "medicine",
            "medication",
            "prescription",
            "phac do dieu tri"
    );

    private static final List<String> DIAGNOSIS_PHRASES = List.of(
            "toi bi benh gi",
            "toi bi gi",
            "bi benh gi",
            "lieu toi co mac benh gi khong",
            "co kha nang la benh gi",
            "kha nang cao la gi",
            "dau hieu nay giong benh gi",
            "tinh trang nay co dang lo khong",
            "nhu vay co nguy hiem khong",
            "toi co can di cap cuu khong",
            "co phai dau hieu ung thu khong",
            "co phai dau hieu viem ruot thua khong",
            "co phai dau hieu dot quy khong",
            "co phai dau hieu nhoi mau co tim khong",
            "toi co bi benh gi khong",
            "toi co bi viem loet da day khong",
            "toi co phai bi viem loet khong",
            "toi co phai bi trao nguoc khong",
            "co phai toi bi",
            "chan doan",
            "chan doan giup toi",
            "ket luan",
            "ket luan giup toi",
            "ket luan toi bi gi",
            "trieu chung nay la benh gi",
            "dau nhu vay la benh gi",
            "dau nguc nhu vay la benh gi",
            "toi chac bi benh gi",
            "benh nay co nguy hiem khong",
            "diagnose me",
            "what disease do i have"
    );

    private static final List<String> MEDICATION_NAMES = List.of(
            "panadol",
            "hapacol",
            "efferalgan",
            "decolgen",
            "tiffy",
            "eugica",
            "strepsils",
            "prospan",
            "nospa",
            "buscopan",
            "maalox",
            "gastropulgite",
            "domperidone",
            "motilium",
            "cetirizine",
            "loratadine",
            "chlorpheniramine",
            "betadine",
            "oresol",
            "paracetamol",
            "ibuprofen",
            "aspirin",
            "omeprazole",
            "berberin",
            "smecta",
            "salonpas",
            "dau gio",
            "thuoc nho mat",
            "thuoc nho mui",
            "thuoc ho",
            "thuoc cam",
            "thuoc dau bung",
            "thuoc dau rang",
            "thuoc dau mat",
            "thuoc di ung",
            "thuoc da day",
            "mieng dan giam dau",
            "khang sinh"
    );

    private static final List<String> MEDICATION_ADVICE_PHRASES = List.of(
            "duoc khong",
            "co nen",
            "nen dung",
            "dung duoc khong",
            "uong duoc khong",
            "tot khong",
            "nen mua",
            "nen uong",
            "uong",
            "dung"
    );

    private static final List<String> MEDICATION_WORKFLOW_PHRASES = List.of(
            "nha thuoc",
            "xem don thuoc",
            "don thuoc cu",
            "don thuoc cua toi",
            "lich su don thuoc",
            "bac si co ke don sau khi kham khong",
            "co ke don sau khi kham khong",
            "ke don sau khi kham"
    );

    private PublicAssistantSafety() {
    }

    static Optional<SafetyBlock> detect(String question) {
        String normalized = searchable(question);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (isEmergency(normalized)) {
            return Optional.of(SafetyBlock.emergency());
        }
        if (isMedicationWorkflowQuestion(normalized)) {
            return Optional.empty();
        }
        if (isMedicationOrTreatmentPlan(normalized)) {
            return Optional.of(SafetyBlock.medication());
        }
        if (isDiagnosisOrConclusion(normalized)) {
            return Optional.of(SafetyBlock.diagnosis());
        }
        return Optional.empty();
    }

    static boolean isMedicationOrTreatmentPlan(String normalized) {
        if (containsAnyPhrase(normalized, MEDICATION_PHRASES)) {
            return true;
        }
        if (containsAnyPhrase(normalized, MEDICATION_NAMES)
                && containsAnyPhrase(normalized, MEDICATION_ADVICE_PHRASES)) {
            return true;
        }
        if ((containsPhrase(normalized, "uong") || containsPhrase(normalized, "dung"))
                && containsAnyPhrase(normalized, MEDICATION_NAMES)) {
            return true;
        }
        return containsPhrase(normalized, "uong")
                && containsPhrase(normalized, "bao nhieu")
                && (containsPhrase(normalized, "thuoc")
                || containsPhrase(normalized, "paracetamol")
                || containsPhrase(normalized, "khang sinh"));
    }

    static boolean isDiagnosisOrConclusion(String normalized) {
        if (containsAnyPhrase(normalized, DIAGNOSIS_PHRASES)) {
            return true;
        }
        if (containsPhrase(normalized, "la benh gi")) {
            return true;
        }
        if (containsPhrase(normalized, "co phai")
                && (containsPhrase(normalized, "bi") || containsPhrase(normalized, "benh"))) {
            return true;
        }
        return containsPhrase(normalized, "co bi")
                && (containsPhrase(normalized, "ung thu")
                || containsPhrase(normalized, "viem loet")
                || containsPhrase(normalized, "viem")
                || containsPhrase(normalized, "benh"));
    }

    static boolean isMedicationWorkflowQuestion(String normalized) {
        return containsAnyPhrase(normalized, MEDICATION_WORKFLOW_PHRASES);
    }

    static boolean isEmergency(String normalized) {
        if (containsAnyPhrase(normalized, EMERGENCY_PHRASES)) {
            return true;
        }
        return containsPhrase(normalized, "dau nguc")
                && (containsPhrase(normalized, "du doi")
                || containsPhrase(normalized, "kho tho")
                || containsPhrase(normalized, "ngat")
                || containsPhrase(normalized, "va mo hoi"));
    }

    private static boolean containsAnyPhrase(String normalized, List<String> phrases) {
        for (String phrase : phrases) {
            if (containsPhrase(normalized, phrase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPhrase(String normalized, String phrase) {
        String normalizedPhrase = searchable(phrase);
        if (normalized == null || normalized.isBlank() || normalizedPhrase.isBlank()) {
            return false;
        }
        return (" " + normalized + " ").contains(" " + normalizedPhrase + " ");
    }

    record SafetyBlock(String intent, String message) {
        static SafetyBlock medication() {
            return new SafetyBlock("MEDICATION_SAFETY_BLOCK", MEDICATION_BLOCK_MESSAGE);
        }

        static SafetyBlock diagnosis() {
            return new SafetyBlock("DIAGNOSIS_SAFETY_BLOCK", DIAGNOSIS_BLOCK_MESSAGE);
        }

        static SafetyBlock emergency() {
            return new SafetyBlock("EMERGENCY_SAFETY_BLOCK", EMERGENCY_BLOCK_MESSAGE);
        }
    }
}
