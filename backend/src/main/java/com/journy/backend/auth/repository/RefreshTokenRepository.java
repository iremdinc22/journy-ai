package com.journy.backend.auth.repository;

import com.journy.backend.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUserEmailIgnoreCaseAndRevokedFalse(String email);

    List<RefreshToken> findByExpiresAtBeforeOrRevokedTrue(Instant expiresAt);
}
