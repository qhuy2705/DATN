package com.PrimeCare.PrimeCare.modules.appointment.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Component
public class AppointmentCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm");

    public String generate(LocalDate visitDate, Long doctorId, LocalTime slotStart) {
        String datePart = visitDate.format(DATE_FORMAT);
        String doctorPart = String.format("%04d", doctorId % 10000);
        String timePart = slotStart.format(TIME_FORMAT);

        return "PC-" + datePart + "-" + doctorPart + "-" + timePart + "-" + randomSuffix(4);
    }

    private String randomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}