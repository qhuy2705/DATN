package com.PrimeCare.PrimeCare.shared.email;

import com.PrimeCare.PrimeCare.config.MailSenderProperties;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Component
public class MailSenderIdentity {

    private static final String DEFAULT_FROM_NAME = "H\u1EC7 th\u1ED1ng y t\u1EBF PrimeCare";
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private final MailSenderProperties mailSenderProperties;
    private final String smtpUsername;

    public MailSenderIdentity(
            MailSenderProperties mailSenderProperties,
            @Value("${spring.mail.username:}") String smtpUsername
    ) {
        this.mailSenderProperties = mailSenderProperties;
        this.smtpUsername = smtpUsername;
    }

    public InternetAddress internetAddress() {
        try {
            InternetAddress from = new InternetAddress(resolveFromAddress());
            from.setPersonal(resolveFromName(), StandardCharsets.UTF_8.name());
            return from;
        } catch (AddressException ex) {
            throw new IllegalStateException("Mail sender address is invalid", ex);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException("Mail sender display name must be UTF-8 encodable", ex);
        }
    }

    public String resolveFromAddress() {
        String fromAddress = firstNonBlank(mailSenderProperties.getFromAddress(), smtpUsername);
        if (fromAddress == null) {
            throw new IllegalStateException("Mail sender address is not configured");
        }
        return fromAddress;
    }

    public String resolveFromName() {
        return resolveFromName(mailSenderProperties.getFromName());
    }

    private String resolveFromName(String configuredName) {
        String fromName = firstNonBlank(configuredName, DEFAULT_FROM_NAME);
        if (looksLikeMojibake(fromName)) {
            String fixed = fixMojibake(fromName);
            if (fixed != null && !fixed.isBlank() && !looksLikeMojibake(fixed)) {
                return fixed.trim();
            }
            return DEFAULT_FROM_NAME;
        }
        return fromName;
    }

    private boolean looksLikeMojibake(String value) {
        return value != null
                && (value.contains("Ã") || value.contains("á»") || value.contains("áº"));
    }

    private String fixMojibake(String value) {
        String fixed = decodeMojibake(value, WINDOWS_1252);
        if (fixed != null && !looksLikeMojibake(fixed) && !fixed.contains("?")) {
            return fixed;
        }
        return decodeMojibake(value, StandardCharsets.ISO_8859_1);
    }

    private String decodeMojibake(String value, Charset sourceCharset) {
        return new String(value.getBytes(sourceCharset), StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isBlank()) {
            return first.trim();
        }
        if (second != null && !second.trim().isBlank()) {
            return second.trim();
        }
        return null;
    }
}
