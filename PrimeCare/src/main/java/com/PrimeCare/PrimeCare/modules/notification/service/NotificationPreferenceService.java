package com.PrimeCare.PrimeCare.modules.notification.service;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.PrimeCare.PrimeCare.modules.notification.dto.response.NotificationPreferenceResponse;
import com.PrimeCare.PrimeCare.modules.notification.entity.NotificationPreference;
import com.PrimeCare.PrimeCare.modules.notification.repository.NotificationPreferenceRepository;
import com.PrimeCare.PrimeCare.shared.enums.NotificationChannel;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return toResponse(resolvePreference(user), user);
    }

    @Transactional
    public NotificationPreferenceResponse updateForUser(Long userId, UpdateNotificationPreferenceRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        NotificationPreference pref = resolvePreference(user);
        if (req.getAllowEmail() != null) pref.setAllowEmail(req.getAllowEmail());
        if (req.getAllowSms() != null) pref.setAllowSms(req.getAllowSms());
        if (req.getAppointmentReminders() != null) pref.setAppointmentReminders(req.getAppointmentReminders());
        if (req.getResultReadyAlerts() != null) pref.setResultReadyAlerts(req.getResultReadyAlerts());
        if (req.getInvoiceUpdates() != null) pref.setInvoiceUpdates(req.getInvoiceUpdates());
        if (req.getSecurityAlerts() != null) pref.setSecurityAlerts(req.getSecurityAlerts());
        if (req.getPreferredChannel() != null) pref.setPreferredChannel(req.getPreferredChannel());

        pref = repository.save(pref);
        return toResponse(pref, user);
    }

    private NotificationPreference resolvePreference(User user) {
        return repository.findByUser_Id(user.getId())
                .orElseGet(() -> repository.save(NotificationPreference.builder()
                        .user(user)
                        .allowEmail(user.getEmail() != null && !user.getEmail().isBlank())
                        .allowSms(user.getPhone() != null && !user.getPhone().isBlank())
                        .appointmentReminders(true)
                        .resultReadyAlerts(true)
                        .invoiceUpdates(true)
                        .securityAlerts(true)
                        .preferredChannel(resolvePreferredChannel(user))
                        .build()));
    }

    private NotificationChannel resolvePreferredChannel(User user) {
        if (user.getPhone() != null && !user.getPhone().isBlank()) return NotificationChannel.SMS;
        return NotificationChannel.EMAIL;
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference pref, User user) {
        return NotificationPreferenceResponse.builder()
                .userId(user.getId())
                .allowEmail(pref.getAllowEmail())
                .allowSms(pref.getAllowSms())
                .appointmentReminders(pref.getAppointmentReminders())
                .resultReadyAlerts(pref.getResultReadyAlerts())
                .invoiceUpdates(pref.getInvoiceUpdates())
                .securityAlerts(pref.getSecurityAlerts())
                .preferredChannel(pref.getPreferredChannel())
                .maskedEmail(mask(user.getEmail()))
                .maskedPhone(mask(user.getPhone()))
                .build();
    }

    private String mask(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.length() <= 4) return "****";
        return raw.substring(0, 2) + "***" + raw.substring(raw.length() - 2);
    }
}
