package com.flashsale.auth.support;

import java.time.Duration;
import java.time.Instant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.flashsale.common.metrics.BusinessMetrics;

/**
 * Revoked access tokens (by {@code jti}) kept in Redis until they would expire anyway,
 * so a logout is honoured by every instance.
 * <p>Fail-open: while Redis is unavailable the check is skipped (logged + {@code redis_fail_open_total}); a token
 * logged out during the outage stays valid until it expires (≤ 15 min). Refresh tokens are revoked in PostgreSQL
 * regardless.
 */
@Slf4j
@Component
public class JwtBlacklist implements OAuth2TokenValidator<Jwt> {

    private static final String PREFIX = "jwt:blacklist:";
    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "Token has been revoked", null);

    private final StringRedisTemplate redis;
    private final BusinessMetrics metrics;

    public JwtBlacklist(StringRedisTemplate redis, BusinessMetrics metrics) {
        this.redis = redis;
        this.metrics = metrics;
    }

    public void revoke(String jti, Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (jti != null && !remaining.isNegative() && !remaining.isZero()) {
            try {
                redis.opsForValue().set(PREFIX + jti, "1", remaining);
            } catch (DataAccessException ex) {
                metrics.redisFailOpen("jwt_blacklist");
                log.warn("Could not blacklist token (Redis unavailable); it expires in {}s", remaining.toSeconds());
            }
        }
    }

    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
        } catch (DataAccessException ex) {
            metrics.redisFailOpen("jwt_blacklist");
            log.warn("JWT blacklist fail-open (Redis unavailable): {}", ex.getMessage());
            return false;
        }
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
