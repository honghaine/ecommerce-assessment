package com.flashsale.auth.support;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Revoked access tokens (by {@code jti}) kept in Redis until they would expire anyway,
 * so a logout is honoured by every instance.
 */
@Component
public class JwtBlacklist implements OAuth2TokenValidator<Jwt> {

    private static final String PREFIX = "jwt:blacklist:";
    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "Token has been revoked", null);

    private final StringRedisTemplate redis;

    public JwtBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void revoke(String jti, Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (jti != null && !remaining.isNegative() && !remaining.isZero()) {
            redis.opsForValue().set(PREFIX + jti, "1", remaining);
        }
    }

    public boolean isRevoked(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getId() == null) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        return isRevoked(token.getId()) ? OAuth2TokenValidatorResult.failure(REVOKED)
                : OAuth2TokenValidatorResult.success();
    }
}
