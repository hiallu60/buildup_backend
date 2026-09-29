package com.buildup.auth.dto;

import java.time.Instant;

public record TokenResponse(
        String tokenType,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt
) {

    public static TokenResponse bearer(
            String accessToken,
            String refreshToken,
            Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt
    ) {
        return new TokenResponse(
                "Bearer",
                accessToken,
                refreshToken,
                accessTokenExpiresAt,
                refreshTokenExpiresAt
        );
    }
}
