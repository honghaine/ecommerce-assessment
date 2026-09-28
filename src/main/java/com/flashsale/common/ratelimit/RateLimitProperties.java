package com.flashsale.common.ratelimit;

import java.time.Duration;
import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Rate limit rules per scope, e.g. {@code app.rate-limit.rules.login-ip.limit=20}.
 */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(Map<String, Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? Map.of() : Map.copyOf(rules);
    }

    public record Rule(@Min(1) int limit, @NotNull Duration window) {
    }
}
