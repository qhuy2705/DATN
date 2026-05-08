package com.PrimeCare.PrimeCare.shared.pdf;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
import com.PrimeCare.PrimeCare.shared.enums.PaymentMethod;
import com.PrimeCare.PrimeCare.shared.enums.PaymentStatus;
import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PdfFormatters {

    public static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] DIGITS = {
            "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
    };
    private static final String[] UNITS = {"", "nghìn", "triệu", "tỷ"};

    private PdfFormatters() {
    }

    public static String formatDate(LocalDate value) {
        return value == null ? null : DATE.format(value);
    }

    public static String formatDateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    public static String formatTime(LocalTime value) {
        return value == null ? null : TIME.format(value);
    }

    public static String formatMoneyVnd(Long amount) {
        long safeAmount = amount == null ? 0L : amount;
        return NumberFormat.getNumberInstance(VIETNAMESE).format(safeAmount) + " VNĐ";
    }

    public static String numberToVietnameseWords(Long amount) {
        long value = amount == null ? 0L : amount;
        if (value == 0L) {
            return "Không đồng";
        }
        if (value < 0L) {
            return "Âm " + lowerFirst(numberToVietnameseWords(Math.abs(value)));
        }

        List<Integer> groups = new ArrayList<>();
        while (value > 0L) {
            groups.add(0, (int) (value % 1000));
            value /= 1000;
        }

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            int group = groups.get(i);
            if (group == 0) {
                continue;
            }
            int unitIndex = groups.size() - 1 - i;
            boolean full = i > 0 && group < 100;
            String groupText = readTriple(group, full);
            String unit = unitIndex < UNITS.length ? UNITS[unitIndex] : UNITS[unitIndex % 3] + repeatTy(unitIndex / 3);
            parts.add((groupText + " " + unit).trim());
        }

        String result = String.join(" ", parts).replaceAll("\\s+", " ").trim() + " đồng";
        return uppercaseFirst(result);
    }

    public static String safeText(String value) {
        return safeText(value, "Chưa ghi nhận");
    }

    public static String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        if ("null".equalsIgnoreCase(trimmed) || "undefined".equalsIgnoreCase(trimmed) || "Optional.empty".equalsIgnoreCase(trimmed)) {
            return fallback;
        }
        return trimmed;
    }

    public static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return safeText(value);
    }

    public static String uppercaseVietnamese(String value) {
        return safeText(value).toUpperCase(VIETNAMESE);
    }

    public static String ageOrBirthYear(LocalDate dob) {
        if (dob == null) {
            return "Chưa ghi nhận";
        }
        int years = Period.between(dob, LocalDate.now()).getYears();
        if (years > 0) {
            return years + " tuổi (" + dob.getYear() + ")";
        }
        int months = Math.max(Period.between(dob, LocalDate.now()).getMonths(), 0);
        return months + " tháng (" + dob.getYear() + ")";
    }

    public static String genderLabel(Gender gender) {
        if (gender == null) {
            return "Chưa ghi nhận";
        }
        return switch (gender) {
            case MALE -> "Nam";
            case FEMALE -> "Nữ";
            case OTHER -> "Khác";
        };
    }

    public static String sessionLabel(BranchSessionType session) {
        if (session == null) {
            return "Đang cập nhật";
        }
        return switch (session) {
            case AM -> "Buổi sáng";
            case PM -> "Buổi chiều";
        };
    }

    public static String appointmentStatusLabel(AppointmentStatus status) {
        if (status == null) {
            return "Đang xử lý";
        }
        return switch (status) {
            case REQUESTED -> "Chờ xác nhận";
            case CONFIRMED -> "Đã xác nhận";
            case CHECKED_IN -> "Đã check-in";
            case COMPLETED -> "Đã hoàn tất";
            case CANCELLED -> "Đã hủy";
            case NO_SHOW -> "Không đến";
        };
    }

    public static String paymentStatusLabel(PaymentStatus status) {
        if (status == null) {
            return "Chưa thanh toán";
        }
        return switch (status) {
            case UNPAID -> "Chưa thanh toán";
            case PENDING_CONFIRMATION -> "Chờ xác nhận thanh toán";
            case PAYMENT_REVIEW -> "Đang đối soát";
            case PAID -> "Đã thanh toán";
            case REFUNDED -> "Đã hoàn tiền";
            case VOID -> "Đã hủy";
        };
    }

    public static String paymentMethodLabel(PaymentMethod method) {
        if (method == null) {
            return "Không áp dụng";
        }
        return switch (method) {
            case CASH -> "Tiền mặt";
            case CARD -> "Thẻ";
            case BANK_TRANSFER -> "Chuyển khoản";
            case VNPAY -> "VNPay";
            case EWALLET -> "Ví điện tử";
        };
    }

    public static String serviceResultStatusLabel(ServiceResultStatus status) {
        if (status == null) {
            return "Đang cập nhật";
        }
        return switch (status) {
            case DRAFT -> "Chờ kết quả";
            case COMPLETED -> "Đã có kết quả";
            case VERIFIED -> "Đã xác nhận";
        };
    }

    public static String prescriptionStatusLabel(PrescriptionStatus status) {
        if (status == null) {
            return "Đang cập nhật";
        }
        return switch (status) {
            case DRAFT -> "Bản nháp";
            case ISSUED -> "Đã kê, chờ thanh toán";
            case PAID -> "Đã thanh toán";
            case DISPENSED -> "Đã phát thuốc";
            case CANCELLED -> "Đã hủy";
        };
    }

    public static String userDisplayName(User user) {
        if (user == null) {
            return "Đang cập nhật";
        }
        String fullName = user.getFullName();
        if (fullName != null && !fullName.isBlank() && !"Unknown User".equals(fullName)) {
            return fullName.trim();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim();
        }
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            return user.getPhone().trim();
        }
        return "Đang cập nhật";
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public static String joinNonBlank(String separator, String... values) {
        List<String> items = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    items.add(value.trim());
                }
            }
        }
        return items.isEmpty() ? null : String.join(separator, items);
    }

    private static String readTriple(int number, boolean full) {
        int hundred = number / 100;
        int ten = (number / 10) % 10;
        int one = number % 10;
        List<String> parts = new ArrayList<>();

        if (hundred > 0) {
            parts.add(DIGITS[hundred] + " trăm");
        } else if (full && (ten > 0 || one > 0)) {
            parts.add("không trăm");
        }

        if (ten > 1) {
            parts.add(DIGITS[ten] + " mươi");
            if (one == 1) {
                parts.add("mốt");
            } else if (one == 5) {
                parts.add("lăm");
            } else if (one > 0) {
                parts.add(DIGITS[one]);
            }
        } else if (ten == 1) {
            parts.add("mười");
            if (one == 5) {
                parts.add("lăm");
            } else if (one > 0) {
                parts.add(DIGITS[one]);
            }
        } else if (one > 0) {
            if (hundred > 0 || full) {
                parts.add("lẻ");
            }
            parts.add(DIGITS[one]);
        }

        return String.join(" ", parts);
    }

    private static String repeatTy(int count) {
        if (count <= 0) {
            return "";
        }
        return " " + "tỷ ".repeat(count).trim();
    }

    private static String uppercaseFirst(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(VIETNAMESE) + value.substring(1);
    }

    private static String lowerFirst(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toLowerCase(VIETNAMESE) + value.substring(1);
    }
}
