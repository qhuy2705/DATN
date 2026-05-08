package com.PrimeCare.PrimeCare.shared.email;

import com.PrimeCare.PrimeCare.config.MailSenderProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MailSenderIdentityTest {

    private static final String DEFAULT_FROM_NAME = "Hệ thống y tế PrimeCare";

    @Test
    void resolveFromNameFallsBackToVietnameseDefaultWhenBlank() {
        MailSenderProperties properties = new MailSenderProperties();
        properties.setFromAddress("smtp@example.com");
        properties.setFromName(" ");

        MailSenderIdentity identity = new MailSenderIdentity(properties, "smtp@example.com");

        assertThat(identity.resolveFromName()).isEqualTo(DEFAULT_FROM_NAME);
    }

    @Test
    void resolveFromNameFixesMojibakeFromEnvironment() {
        MailSenderProperties properties = new MailSenderProperties();
        properties.setFromAddress("smtp@example.com");
        properties.setFromName(mojibake(DEFAULT_FROM_NAME));

        MailSenderIdentity identity = new MailSenderIdentity(properties, "smtp@example.com");

        assertThat(identity.resolveFromName()).isEqualTo(DEFAULT_FROM_NAME);
    }

    @Test
    void internetAddressUsesUtf8PersonalName() {
        MailSenderProperties properties = new MailSenderProperties();
        properties.setFromAddress("smtp@example.com");
        properties.setFromName(mojibake(DEFAULT_FROM_NAME));

        MailSenderIdentity identity = new MailSenderIdentity(properties, "smtp@example.com");

        assertThat(identity.internetAddress().getPersonal()).isEqualTo(DEFAULT_FROM_NAME);
        assertThat(identity.internetAddress().getAddress()).isEqualTo("smtp@example.com");
    }

    private String mojibake(String value) {
        return new String(value.getBytes(StandardCharsets.UTF_8), Charset.forName("windows-1252"));
    }
}
