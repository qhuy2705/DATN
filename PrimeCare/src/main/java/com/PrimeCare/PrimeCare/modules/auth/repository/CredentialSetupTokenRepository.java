package com.PrimeCare.PrimeCare.modules.auth.repository;

import com.PrimeCare.PrimeCare.modules.auth.entity.CredentialSetupToken;
import com.PrimeCare.PrimeCare.shared.enums.CredentialSetupTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CredentialSetupTokenRepository extends JpaRepository<CredentialSetupToken, Long> {
    Optional<CredentialSetupToken> findByTokenHash(String tokenHash);
    List<CredentialSetupToken> findAllByUser_IdAndPurposeAndUsedAtIsNull(Long userId, CredentialSetupTokenPurpose purpose);
}
