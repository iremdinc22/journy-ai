package com.journy.backend.auth.service;

import com.journy.backend.auth.model.RefreshToken;
import com.journy.backend.auth.repository.RefreshTokenRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.user.model.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final CurrentUserService currentUserService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long expirationSeconds;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            CurrentUserService currentUserService,
            @Value("${app.security.refresh-token-expiration-seconds}") long expirationSeconds
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.currentUserService = currentUserService;
        this.expirationSeconds = expirationSeconds;
    }

    @Transactional
    public RefreshToken create(UserAccount user) {
        byte[] tokenBytes = new byte[48];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        return refreshTokenRepository.save(new RefreshToken(
                token,
                user,
                Instant.now().plusSeconds(expirationSeconds)
        ));
    }

    @Transactional
    public UserAccount verifyAndRotate(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token"));

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Refresh token expired or revoked");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getUser();
    }

    @Transactional
    public void revoke(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token"));
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public void revokeAllForCurrentUser() {
        UserAccount user = currentUserService.currentUser();
        refreshTokenRepository.findByUserEmailIgnoreCaseAndRevokedFalse(user.getEmail())
                .forEach(token -> token.setRevoked(true));
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredAndRevokedTokens() {
        refreshTokenRepository.deleteAll(refreshTokenRepository.findByExpiresAtBeforeOrRevokedTrue(Instant.now()));
    }
}
