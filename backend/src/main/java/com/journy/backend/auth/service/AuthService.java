package com.journy.backend.auth.service;

import com.journy.backend.auth.model.RefreshToken;
import com.journy.backend.auth.dto.AuthResponse;
import com.journy.backend.auth.dto.LoginRequest;
import com.journy.backend.auth.dto.RefreshTokenRequest;
import com.journy.backend.auth.dto.RegisterRequest;
import com.journy.backend.security.JwtService;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class AuthService {
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse register(RegisterRequest request) {
        if (userAccountRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(CONFLICT, "Email is already registered");
        }

        UserAccount user = new UserAccount(
                request.fullName(),
                request.email(),
                passwordEncoder.encode(request.password()),
                "Balanced traveler"
        );
        return buildAuthResponse(userAccountRepository.save(user));
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        UserAccount user = refreshTokenService.verifyAndRotate(request.refreshToken());
        return buildAuthResponse(user);
    }

    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    public void logoutAll() {
        refreshTokenService.revokeAllForCurrentUser();
    }

    private AuthResponse buildAuthResponse(UserAccount user) {
        RefreshToken refreshToken = refreshTokenService.create(user);
        return new AuthResponse(
                jwtService.generateToken(user),
                refreshToken.getToken(),
                "Bearer",
                new AuthResponse.UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getTravelStyle())
        );
    }
}
