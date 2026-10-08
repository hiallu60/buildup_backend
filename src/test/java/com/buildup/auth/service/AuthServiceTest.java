package com.buildup.auth.service;

import com.buildup.auth.dto.TokenResponse;
import com.buildup.auth.jwt.JwtTokenService;
import com.buildup.user.entity.User;
import com.buildup.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void refreshIssuesOnlyNewAccessTokenAndKeepsRefreshToken() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordService passwordService = mock(PasswordService.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        AuthService authService = new AuthService(
                userRepository,
                passwordService,
                jwtTokenService,
                refreshTokenService
        );
        User user = mock(User.class);
        Instant accessExpiresAt = Instant.parse("2030-01-01T00:15:00Z");
        Instant refreshExpiresAt = Instant.parse("2030-01-14T00:00:00Z");
        String rawRefreshToken = "existing-refresh-token";
        when(refreshTokenService.validate(rawRefreshToken))
                .thenReturn(new RefreshTokenService.ValidatedRefreshToken(user, refreshExpiresAt));
        when(jwtTokenService.issueAccessToken(user))
                .thenReturn(new JwtTokenService.AccessToken("new-access-token", accessExpiresAt));

        TokenResponse response = authService.refresh(rawRefreshToken);

        assertEquals("new-access-token", response.accessToken());
        assertEquals(rawRefreshToken, response.refreshToken());
        assertEquals(accessExpiresAt, response.accessTokenExpiresAt());
        assertEquals(refreshExpiresAt, response.refreshTokenExpiresAt());
        verify(refreshTokenService).validate(rawRefreshToken);
        verify(jwtTokenService).issueAccessToken(user);
    }
}
