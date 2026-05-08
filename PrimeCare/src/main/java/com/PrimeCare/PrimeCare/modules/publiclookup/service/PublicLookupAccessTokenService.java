package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.config.PublicLookupOtpProperties;
import com.PrimeCare.PrimeCare.modules.publiclookup.entity.PublicLookupAccessToken;
import com.PrimeCare.PrimeCare.modules.publiclookup.repository.PublicLookupAccessTokenRepository;
import com.PrimeCare.PrimeCare.shared.enums.PublicLookupType;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicLookupAccessTokenService {
    private final PublicLookupAccessTokenRepository repository;
    private final PublicLookupOtpProperties otpProperties;

    @Transactional
    public PublicLookupAccessToken issue(PublicLookupType type, Long referenceId, String referenceCode) {
        PublicLookupAccessToken token = PublicLookupAccessToken.builder()
                                                               .token(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""))
                                                               .lookupType(type)
                                                               .referenceId(referenceId)
                                                               .referenceCode(referenceCode)
                                                               .expiresAt(LocalDateTime.now().plusMinutes(otpProperties.getAccessTokenTtlMinutes()))
                                                               .build();
        return repository.save(token);
    }

    @Transactional(readOnly = true)
    public PublicLookupAccessToken verify(String token, PublicLookupType type, Long referenceId) {
        String lookupToken = token == null ? "" : token.trim();
        if (lookupToken.isBlank()) {
            throw new ApiException(ErrorCode.PUBLIC_LOOKUP_TOKEN_INVALID);
        }

        PublicLookupAccessToken accessToken = repository.findByToken(lookupToken)
                                                        .orElseThrow(() -> new ApiException(ErrorCode.PUBLIC_LOOKUP_TOKEN_INVALID));
        if (accessToken.getLookupType() != type || !referenceId.equals(accessToken.getReferenceId())) {
            throw new ApiException(ErrorCode.PUBLIC_LOOKUP_TOKEN_INVALID);
        }
        if (accessToken.getExpiresAt() == null || !accessToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.PUBLIC_LOOKUP_TOKEN_EXPIRED);
        }
        return accessToken;
    }
}
