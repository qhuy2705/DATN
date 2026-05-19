package com.PrimeCare.PrimeCare.modules.bookingemailotp.repository;

import com.PrimeCare.PrimeCare.modules.bookingemailotp.entity.BookingEmailOtp;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.entity.BookingEmailOtpPurpose;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.entity.BookingEmailOtpStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface BookingEmailOtpRepository extends JpaRepository<BookingEmailOtp, Long> {
    Optional<BookingEmailOtp> findTopByPurposeAndNormalizedEmailOrderByCreatedAtDesc(
            BookingEmailOtpPurpose purpose,
            String normalizedEmail
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BookingEmailOtp> findByVerificationId(String verificationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BookingEmailOtp otp
            set otp.status = :expiredStatus
            where otp.purpose = :purpose
              and otp.normalizedEmail = :normalizedEmail
              and otp.status in :activeStatuses
              and otp.consumedAt is null
            """)
    int expireActiveOtps(
            @Param("purpose") BookingEmailOtpPurpose purpose,
            @Param("normalizedEmail") String normalizedEmail,
            @Param("activeStatuses") Collection<BookingEmailOtpStatus> activeStatuses,
            @Param("expiredStatus") BookingEmailOtpStatus expiredStatus
    );
}
