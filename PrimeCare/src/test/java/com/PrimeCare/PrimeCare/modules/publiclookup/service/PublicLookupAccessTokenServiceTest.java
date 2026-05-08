package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.config.PublicLookupOtpProperties;
import com.PrimeCare.PrimeCare.modules.publiclookup.entity.PublicLookupAccessToken;
import com.PrimeCare.PrimeCare.modules.publiclookup.repository.PublicLookupAccessTokenRepository;
import com.PrimeCare.PrimeCare.shared.enums.PublicLookupType;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicLookupAccessTokenServiceTest {

    @Mock
    private PublicLookupAccessTokenRepository repository;
    @Mock
    private PublicLookupOtpProperties otpProperties;

    private PublicLookupAccessTokenService service;

    @BeforeEach
    void setUp() {
        service = new PublicLookupAccessTokenService(repository, otpProperties);
    }

    @Test
    void verifyRejectsMissingLookupTokenWithoutUnauthorizedStatus() {
        when(repository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("bad-token", PublicLookupType.APPOINTMENT, 7L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PUBLIC_LOOKUP_TOKEN_INVALID);
                    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getErrorCode().getStatus()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("Token tra cứu không hợp lệ.");
                });
    }

    @Test
    void verifyRejectsMismatchedLookupTokenAsInvalidPublicLookupToken() {
        PublicLookupAccessToken token = PublicLookupAccessToken.builder()
                .token("lookup-token")
                .lookupType(PublicLookupType.RESULT)
                .referenceId(99L)
                .referenceCode("ENC001")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        when(repository.findByToken("lookup-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verify("lookup-token", PublicLookupType.APPOINTMENT, 7L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PUBLIC_LOOKUP_TOKEN_INVALID);
                    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void verifyRejectsExpiredLookupTokenWithoutAuthTokenExpired() {
        PublicLookupAccessToken token = PublicLookupAccessToken.builder()
                .token("lookup-token")
                .lookupType(PublicLookupType.APPOINTMENT)
                .referenceId(7L)
                .referenceCode("APT001")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(repository.findByToken("lookup-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verify("lookup-token", PublicLookupType.APPOINTMENT, 7L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PUBLIC_LOOKUP_TOKEN_EXPIRED);
                    assertThat(ex.getErrorCode()).isNotEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
                    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Token tra cứu đã hết hạn.");
                });
    }

    @Test
    void authTokenInvalidStillMapsToUnauthorizedForRealAuthSessionFailures() {
        assertThat(ErrorCode.AUTH_TOKEN_INVALID.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTH_TOKEN_EXPIRED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
