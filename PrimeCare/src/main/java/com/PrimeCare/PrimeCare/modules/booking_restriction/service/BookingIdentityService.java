package com.PrimeCare.PrimeCare.modules.booking_restriction.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class BookingIdentityService {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private final String identityPepper;

    public BookingIdentityService(
            @Value("${app.booking-restriction.identity-pepper:${app.jwt.secret:primecare_booking_restriction_dev_pepper_change_me}}")
            String identityPepper
    ) {
        this.identityPepper = identityPepper;
    }

    public BookingIdentity resolvePublicIdentity(
            String phone,
            String fullName,
            LocalDate dob,
            String email
    ) {
        String canonicalPhone = normalizePhone(phone);
        String normalizedName = normalizeFullName(fullName);
        String normalizedEmail = normalizeEmail(email);

        if (canonicalPhone != null) {
            if (dob != null) {
                return new BookingIdentity(
                        hashIdentityKey("PUBLIC_PHONE_DOB|" + canonicalPhone + "|" + dob),
                        canonicalPhone,
                        normalizedEmail
                );
            }
            if (normalizedName != null) {
                return new BookingIdentity(
                        hashIdentityKey("PUBLIC_PHONE_NAME|" + canonicalPhone + "|" + normalizedName),
                        canonicalPhone,
                        normalizedEmail
                );
            }
            return new BookingIdentity(
                    hashIdentityKey("PUBLIC_PHONE|" + canonicalPhone),
                    canonicalPhone,
                    normalizedEmail
            );
        }

        if (normalizedEmail != null) {
            return new BookingIdentity(
                    hashIdentityKey("PUBLIC_EMAIL|" + normalizedEmail),
                    null,
                    normalizedEmail
            );
        }

        throw new ApiException(ErrorCode.VALIDATION_ERROR, "Không đủ thông tin định danh để đặt lịch.");
    }

    public BookingIdentity resolveAppointmentIdentity(Appointment appointment) {
        if (appointment == null) {
            throw new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        return resolvePublicIdentity(
                appointment.getPatientPhone(),
                appointment.getPatientFullName(),
                appointment.getPatientDob(),
                appointment.getPatientEmail()
        );
    }

    public BookingIdentity resolveStaffVerifiedPatientIdentity(Patient patient) {
        if (patient == null) {
            throw new ApiException(ErrorCode.PATIENT_NOT_FOUND);
        }
        if (isReliableVerifiedPatientProfile(patient)) {
            return new BookingIdentity(
                    hashIdentityKey("PATIENT_ID|" + patient.getId()),
                    normalizePhone(patient.getPhone()),
                    normalizeEmail(patient.getEmail())
            );
        }
        return resolvePublicIdentity(patient.getPhone(), patient.getFullName(), patient.getDob(), patient.getEmail());
    }

    public String normalizePhone(String value) {
        String trimmed = StringUtil.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String digits = trimmed.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("0") && digits.length() >= 10) {
            return "84" + digits.substring(1);
        }
        if (digits.startsWith("84")) {
            return digits;
        }
        return digits.isBlank() ? null : digits;
    }

    public List<String> phoneLookupCandidates(String phone) {
        String canonical = normalizePhone(phone);
        if (canonical == null) {
            return List.of();
        }

        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(canonical);
        candidates.add("+" + canonical);
        if (canonical.startsWith("84") && canonical.length() > 2) {
            candidates.add("0" + canonical.substring(2));
        }
        return List.copyOf(candidates);
    }

    public String normalizeEmail(String value) {
        String trimmed = StringUtil.trimToNull(value);
        return trimmed != null ? trimmed.toLowerCase(Locale.ROOT) : null;
    }

    public String normalizeFullName(String value) {
        String trimmed = StringUtil.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String compact = trimmed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String decomposed = Normalizer.normalize(compact, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("");
    }

    private boolean isReliableVerifiedPatientProfile(Patient patient) {
        return false;
    }

    private String hashIdentityKey(String identityKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((identityPepper + "|" + identityKey).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
