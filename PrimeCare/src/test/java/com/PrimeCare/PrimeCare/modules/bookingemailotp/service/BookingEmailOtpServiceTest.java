package com.PrimeCare.PrimeCare.modules.bookingemailotp.service;

import com.PrimeCare.PrimeCare.config.BookingEmailOtpProperties;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.dto.response.BookingEmailOtpRequestResponse;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.entity.BookingEmailOtp;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.entity.BookingEmailOtpPurpose;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.entity.BookingEmailOtpStatus;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.messaging.BookingEmailOtpEmailPublisher;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.repository.BookingEmailOtpRepository;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingEmailOtpServiceTest {

    @Mock
    private BookingEmailOtpRepository otpRepository;
    @Mock
    private BookingEmailOtpEmailPublisher emailPublisher;
    @Mock
    private BookingEmailOtpProperties properties;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookingEmailOtpService service;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getTtlSeconds()).thenReturn(300);
        lenient().when(properties.getTokenTtlSeconds()).thenReturn(600);
        lenient().when(properties.getResendCooldownSeconds()).thenReturn(30);
        lenient().when(properties.getMaxAttempts()).thenReturn(5);
    }

    @Test
    void requestOtpStoresHashAndPublishesBookingEmailAfterCommit() {
        when(otpRepository.findTopByPurposeAndNormalizedEmailOrderByCreatedAtDesc(
                BookingEmailOtpPurpose.GUEST_BOOKING_EMAIL_VERIFICATION,
                "patient@example.com"
        )).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-otp");
        when(otpRepository.save(any(BookingEmailOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingEmailOtpRequestResponse response = service.requestOtp(
                "Patient@Example.com",
                "127.0.0.1",
                "Unit Test"
        );

        assertThat(response.getVerificationId()).isNotBlank();
        assertThat(response.getChannel()).isEqualTo("EMAIL");
        assertThat(response.getMaskedDestination()).isEqualTo("pa***@example.com");
        assertThat(response.getExpiresInSeconds()).isEqualTo(300);

        ArgumentCaptor<BookingEmailOtp> otpCaptor = ArgumentCaptor.forClass(BookingEmailOtp.class);
        verify(otpRepository).save(otpCaptor.capture());
        BookingEmailOtp saved = otpCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("patient@example.com");
        assertThat(saved.getNormalizedEmail()).isEqualTo("patient@example.com");
        assertThat(saved.getOtpHash()).isEqualTo("hashed-otp");
        assertThat(saved.getStatus()).isEqualTo(BookingEmailOtpStatus.PENDING);

        verify(emailPublisher).publishAfterCommit(
                org.mockito.ArgumentMatchers.same(saved),
                org.mockito.ArgumentMatchers.matches("\\d{6}"),
                org.mockito.ArgumentMatchers.eq(300),
                org.mockito.ArgumentMatchers.eq(30)
        );
    }

    @Test
    void verifyOtpIssuesBookingTokenAndMarksLoggedInAccountEmailVerified() {
        BookingEmailOtp otp = pendingOtp();
        User user = User.builder().id(10L).email("PATIENT@example.com").build();
        when(otpRepository.findByVerificationId("verification")).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        var response = service.verifyOtp("verification", "123456", 10L);

        assertThat(response.getBookingEmailVerificationToken()).startsWith("verification.");
        assertThat(response.getExpiresAt()).isNotBlank();
        assertThat(otp.getStatus()).isEqualTo(BookingEmailOtpStatus.VERIFIED);
        assertThat(otp.getVerifiedAt()).isNotNull();
        assertThat(otp.getTokenHash()).startsWith("hash:");
        assertThat(otp.getTokenExpiresAt()).isNotNull();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        verify(otpRepository).save(otp);
        verify(userRepository).save(user);
    }

    @Test
    void validateTokenRejectsExpiredToken() {
        BookingEmailOtp otp = verifiedOtp();
        otp.setTokenExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(otpRepository.findByVerificationId("verification")).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.validateTokenForBooking("verification.secret", "patient@example.com"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOKING_EMAIL_TOKEN_EXPIRED)
                );

        assertThat(otp.getStatus()).isEqualTo(BookingEmailOtpStatus.EXPIRED);
        verify(otpRepository).save(otp);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void validateTokenRejectsTokenForDifferentEmail() {
        BookingEmailOtp otp = verifiedOtp();
        when(otpRepository.findByVerificationId("verification")).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.validateTokenForBooking("verification.secret", "other@example.com"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOKING_EMAIL_TOKEN_INVALID)
                );

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void validateTokenAcceptsMatchingEmail() {
        BookingEmailOtp otp = verifiedOtp();
        when(otpRepository.findByVerificationId("verification")).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("secret", "hashed-token")).thenReturn(true);

        var verified = service.validateTokenForBooking("verification.secret", "PATIENT@example.com");

        assertThat(verified.otp()).isSameAs(otp);
    }

    @Test
    void consumeForBookingMarksTokenConsumed() {
        BookingEmailOtp otp = verifiedOtp();

        service.consumeForBooking(new BookingEmailOtpService.VerifiedBookingEmailToken(otp));

        assertThat(otp.getStatus()).isEqualTo(BookingEmailOtpStatus.CONSUMED);
        assertThat(otp.getConsumedAt()).isNotNull();
        verify(otpRepository).save(otp);
    }

    private BookingEmailOtp pendingOtp() {
        return BookingEmailOtp.builder()
                .email("patient@example.com")
                .normalizedEmail("patient@example.com")
                .verificationId("verification")
                .otpHash("hashed-otp")
                .purpose(BookingEmailOtpPurpose.GUEST_BOOKING_EMAIL_VERIFICATION)
                .status(BookingEmailOtpStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attemptCount(0)
                .build();
    }

    private BookingEmailOtp verifiedOtp() {
        return BookingEmailOtp.builder()
                .email("patient@example.com")
                .normalizedEmail("patient@example.com")
                .verificationId("verification")
                .otpHash("hashed-otp")
                .purpose(BookingEmailOtpPurpose.GUEST_BOOKING_EMAIL_VERIFICATION)
                .status(BookingEmailOtpStatus.VERIFIED)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .verifiedAt(LocalDateTime.now())
                .tokenHash("hashed-token")
                .tokenExpiresAt(LocalDateTime.now().plusMinutes(10))
                .attemptCount(0)
                .build();
    }
}
