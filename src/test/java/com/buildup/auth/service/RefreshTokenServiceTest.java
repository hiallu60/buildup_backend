package com.buildup.auth.service;

import com.buildup.auth.entity.RefreshToken;
import com.buildup.auth.jwt.JwtProperties;
import com.buildup.auth.repository.RefreshTokenRepository;
import com.buildup.common.exception.AuthException;
import com.buildup.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private RefreshTokenRepository refreshTokenRepository;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        JwtProperties properties = new JwtProperties(
                "unused-in-refresh-token-tests",
                "buildup-api",
                Duration.ofMinutes(15),
                Duration.ofDays(14)
        );
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, properties);
    }

    @Test
    void issueStoresOnlyHashedRefreshToken() {
        User user = mock(User.class);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(user);

        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken stored = captor.getValue();
        assertSame(user, stored.getUser());
        assertEquals(64, stored.getTokenHash().length());
        assertNotEquals(issued.value(), stored.getTokenHash());
        assertEquals(issued.expiresAt(), stored.getExpiresAt().toInstant(ZoneOffset.UTC));
    }

    @Test
    void validRefreshTokenCanBeReusedWithoutRotation() {
        User user = mock(User.class);
        RefreshToken token = mock(RefreshToken.class);
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(7);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(token.isRevoked()).thenReturn(false);
        when(token.isExpired(any(LocalDateTime.class))).thenReturn(false);
        when(token.getUser()).thenReturn(user);
        when(token.getExpiresAt()).thenReturn(expiresAt);

        RefreshTokenService.ValidatedRefreshToken first =
                refreshTokenService.validate("same-refresh-token");
        RefreshTokenService.ValidatedRefreshToken second =
                refreshTokenService.validate("same-refresh-token");

        assertSame(user, first.user());
        assertSame(user, second.user());
        assertEquals(expiresAt.toInstant(ZoneOffset.UTC), first.expiresAt());
        verify(token, never()).revoke(any(LocalDateTime.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(refreshTokenRepository, times(2)).findByTokenHash(eq(
                "c531bc47e87cdea669f0fc6453b57a180d789236aed2be4e3a7ae7b3c9ddb3fd"
        ));
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> refreshTokenService.validate("unknown-refresh-token")
        );

        assertEquals("INVALID_REFRESH_TOKEN", exception.getCode());
    }

    @Test
    void revokedRefreshTokenCannotBeReused() {
        RefreshToken token = mock(RefreshToken.class);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(token.isRevoked()).thenReturn(true);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> refreshTokenService.validate("revoked-refresh-token")
        );

        assertEquals("INVALID_REFRESH_TOKEN", exception.getCode());
        verify(token, never()).revoke(any(LocalDateTime.class));
    }

    @Test
    void expiredRefreshTokenIsRevokedAndRejected() {
        RefreshToken token = mock(RefreshToken.class);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(token.isRevoked()).thenReturn(false);
        when(token.isExpired(any(LocalDateTime.class))).thenReturn(true);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> refreshTokenService.validate("expired-refresh-token")
        );

        assertEquals("INVALID_REFRESH_TOKEN", exception.getCode());
        verify(token).revoke(any(LocalDateTime.class));
    }

    @Test
    void logoutRevokesStoredRefreshToken() {
        RefreshToken token = mock(RefreshToken.class);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        refreshTokenService.revoke("refresh-token-to-revoke");

        verify(token).revoke(any(LocalDateTime.class));
    }
}
