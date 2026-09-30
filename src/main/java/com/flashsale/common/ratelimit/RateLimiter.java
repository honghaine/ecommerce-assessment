package com.flashsale.common.ratelimit;

import java.time.Duration;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.common.metrics.BusinessMetrics;

/**
 * Distributed fixed-window rate limiter backed by Redis, shared by all instances.
 * INCR and PEXPIRE run in one Lua script so a key can never be left without a TTL.
 * <p>Fail-open: if Redis is unavailable the request is allowed (logged + {@code redis_fail_open_total}), so a Redis
 * outage degrades throttling instead of taking the API down.
 */
@Slf4j
@Component
public class RateLimiter {

    private static final RedisScript<List> INCREMENT_SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return {count, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final BusinessMetrics metrics;

    public RateLimiter(StringRedisTemplate redis, RateLimitProperties properties, BusinessMetrics metrics) {
        this.redis = redis;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * @param scope rule name from {@code app.rate-limit.rules}
     * @param key   caller key (IP, hashed identifier, ...) — must not contain raw PII
     * @throws ApiException {@link ErrorCode#TOO_MANY_REQUESTS} when the limit is exceeded
     */
    public void check(String scope, String key) {
        RateLimitProperties.Rule rule = properties.rules().get(scope);
        if (rule == null) {
            throw new IllegalStateException("No rate limit rule configured for scope " + scope);
        }
        List<?> result;
        try {
            result = redis.execute(INCREMENT_SCRIPT, List.of("ratelimit:" + scope + ":" + key),
                    String.valueOf(rule.window().toMillis()));
        } catch (DataAccessException ex) {
            metrics.redisFailOpen("rate_limiter");
            log.warn("Rate limiter fail-open (Redis unavailable): {}", ex.getMessage());
            return;
        }
        long count = ((Number) result.get(0)).longValue();
        if (count > rule.limit()) {
            metrics.rateLimited(scope);
            long ttlMillis = ((Number) result.get(1)).longValue();
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, Duration.ofMillis(Math.max(ttlMillis, 1000)));
        }
    }
}
