package com.PrimeCare.PrimeCare.modules.publiclookup.repository;

import com.PrimeCare.PrimeCare.modules.publiclookup.entity.PublicLookupOtp;
import com.PrimeCare.PrimeCare.shared.enums.PublicLookupType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PublicLookupOtpRepository extends JpaRepository<PublicLookupOtp, Long> {
    Optional<PublicLookupOtp> findTopByLookupTypeAndReferenceIdAndReferenceCodeOrderByCreatedAtDesc(
            PublicLookupType lookupType,
            Long referenceId,
            String referenceCode
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PublicLookupOtp otp
            set otp.consumedAt = :consumedAt
            where otp.lookupType = :lookupType
              and otp.referenceId = :referenceId
              and otp.referenceCode = :referenceCode
              and otp.consumedAt is null
            """)
    int consumeActiveOtps(
            @Param("lookupType") PublicLookupType lookupType,
            @Param("referenceId") Long referenceId,
            @Param("referenceCode") String referenceCode,
            @Param("consumedAt") LocalDateTime consumedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PublicLookupOtp otp
            set otp.deleted = true,
                otp.deletedAt = :deletedAt
            where otp.deleted = false
              and otp.expiresAt < :cutoff
            """)
    int softDeleteExpiredBefore(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}
