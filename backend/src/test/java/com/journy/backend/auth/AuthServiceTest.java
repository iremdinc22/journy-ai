package com.journy.backend.auth;

import com.journy.backend.auth.dto.AuthResponse;
import com.journy.backend.auth.dto.LoginRequest;
import com.journy.backend.auth.dto.RegisterRequest;
import com.journy.backend.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {
    @Autowired
    private AuthService authService;

    @Test
    void registerAndLoginReturnsAccessAndRefreshTokens() {
        String email = "auth-" + System.nanoTime() + "@journy.app";

        AuthResponse registered = authService.register(new RegisterRequest("Test User", email, "secret123"));
        AuthResponse loggedIn = authService.login(new LoginRequest(email, "secret123"));

        assertThat(registered.accessToken()).isNotBlank();
        assertThat(registered.refreshToken()).isNotBlank();
        assertThat(loggedIn.accessToken()).isNotBlank();
        assertThat(loggedIn.refreshToken()).isNotBlank();
        assertThat(loggedIn.user().email()).isEqualTo(email);
    }

    @Test
    void loginRejectsWrongPassword() {
        String email = "wrong-" + System.nanoTime() + "@journy.app";
        authService.register(new RegisterRequest("Wrong Password", email, "secret123"));

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "bad-password")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
