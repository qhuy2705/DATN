package com.PrimeCare.PrimeCare.modules.publiclookup.repository;

import com.PrimeCare.PrimeCare.modules.publiclookup.entity.PublicLookupAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PublicLookupAccessTokenRepository extends JpaRepository<PublicLookupAccessToken, Long> {
    Optional<PublicLookupAccessToken> findByToken(String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PublicLookupAccessToken token
            set token.deleted = true,
                token.deletedAt = :deletedAt
            where token.deleted = false
              and token.expiresAt < :cutoff
            """)
    int softDeleteExpiredBefore(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}
