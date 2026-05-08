package com.PrimeCare.PrimeCare.modules.publiclookup.messaging;

import com.PrimeCare.PrimeCare.config.RabbitMqConfig;
import com.PrimeCare.PrimeCare.modules.publiclookup.messaging.event.PublicLookupOtpEmailEvent;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicLookupOtpEmailPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final AfterCommitExecutor afterCommitExecutor;

    public void publishAfterCommit(PublicLookupOtpEmailEvent event) {
        afterCommitExecutor.executeAndPropagate(() -> publish(event));
    }

    private void publish(PublicLookupOtpEmailEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.PUBLIC_LOOKUP_EXCHANGE,
                    RabbitMqConfig.PUBLIC_LOOKUP_OTP_EMAIL_ROUTING_KEY,
                    event
            );
        } catch (RuntimeException ex) {
            log.error(
                    "Publish public lookup OTP email failed lookupType={} referenceCode={} toEmail={}",
                    event != null ? event.lookupType() : null,
                    event != null ? event.referenceCode() : null,
                    event != null ? maskEmail(event.toEmail()) : null,
                    ex
            );
            throw ex;
        }
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(0, at));
        }
        String prefix = email.substring(0, at);
        String domain = email.substring(at);
        if (prefix.length() <= 2) {
            return prefix.charAt(0) + "***" + domain;
        }
        return prefix.substring(0, 2) + "***" + domain;
    }
}
