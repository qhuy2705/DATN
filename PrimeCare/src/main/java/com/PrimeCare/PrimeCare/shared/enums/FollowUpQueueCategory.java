package com.PrimeCare.PrimeCare.shared.enums;

import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public enum FollowUpQueueCategory {
    ALL,
    NO_SHOW,
    DOCTOR_CANCELLATION,
    CONTACT_REQUEST;

    public static FollowUpQueueCategory parse(String value) {
        String normalized = StringUtil.trimToNull(value);
        if (normalized == null) {
            return ALL;
        }
        try {
            return FollowUpQueueCategory.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "Danh mục follow-up không hợp lệ. Giá trị hợp lệ: " + supportedValues()
            );
        }
    }

    public Set<AppointmentFollowUpType> followUpTypes() {
        return switch (this) {
            case ALL -> EnumSet.allOf(AppointmentFollowUpType.class);
            case NO_SHOW -> EnumSet.of(AppointmentFollowUpType.NO_SHOW);
            case DOCTOR_CANCELLATION -> EnumSet.of(
                    AppointmentFollowUpType.DOCTOR_CANCELLATION_NO_RESPONSE,
                    AppointmentFollowUpType.DOCTOR_CANCELLATION_CONTACT_REQUESTED
            );
            case CONTACT_REQUEST -> EnumSet.of(
                    AppointmentFollowUpType.DOCTOR_CANCELLATION_CONTACT_REQUESTED,
                    AppointmentFollowUpType.PATIENT_CONTACT_REQUESTED
            );
        };
    }

    public boolean filtersTypes() {
        return this != ALL;
    }

    public boolean includesLegacyNoShow() {
        return this == ALL || this == NO_SHOW;
    }

    public static FollowUpQueueCategory categoryOf(AppointmentFollowUpType followUpType) {
        if (followUpType == null) {
            return null;
        }
        return switch (followUpType) {
            case NO_SHOW -> NO_SHOW;
            case DOCTOR_CANCELLATION_NO_RESPONSE -> DOCTOR_CANCELLATION;
            case DOCTOR_CANCELLATION_CONTACT_REQUESTED, PATIENT_CONTACT_REQUESTED -> CONTACT_REQUEST;
        };
    }

    private static String supportedValues() {
        return Arrays.stream(values())
                     .map(Enum::name)
                     .collect(Collectors.joining(", "));
    }
}
