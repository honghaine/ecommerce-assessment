package com.flashsale.auth.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(@Valid @NotNull Jwt jwt, @NotNull Duration refreshTokenTtl, @Valid @NotNull Otp otp) {

    /**
     * HS256 with a secret shared by all instances (from env), so any instance can verify
     * any token. Move to RS256/JWKS when other services need to verify tokens.
     */
    public record Jwt(@NotBlank String issuer, @NotBlank @Size(min = 32) String secret,
                      @NotNull Duration accessTokenTtl) {
    }

    /**
     * @param secret       server-side key for HMAC-ing OTP codes and identifiers stored in Redis
     * @param plainStorage DEV/TEST ONLY: store the code and identifier in Redis unhashed so the
     *                     OTP can be read directly (e.g. in RedisInsight). Must be false in production.
     */
    public record Otp(int length, @NotNull Duration ttl, int maxAttempts, @NotNull Duration resendCooldown,
                      @NotBlank @Size(min = 32) String secret, boolean plainStorage) {
    }
}
