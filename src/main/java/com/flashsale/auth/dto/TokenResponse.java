package com.flashsale.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.flashsale.auth.model.TokenPair;

public record TokenResponse(
        @Schema(description = "JWT — paste into Authorize") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access token lifetime (s)", example = "900") long expiresIn,
        String refreshToken,
        @Schema(description = "Refresh token lifetime (s)", example = "604800") long refreshExpiresIn) {

    public static TokenResponse from(TokenPair pair) {
        return new TokenResponse(pair.accessToken(), "Bearer", pair.accessExpiresInSeconds(),
                pair.refreshToken(), pair.refreshExpiresInSeconds());
    }

    @Override
    public String toString() {
        return "TokenResponse[tokenType=Bearer, expiresIn=" + expiresIn + "]";
    }
}
