package com.flashsale.outbox.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param batchSize   max events handled per poll per instance
 * @param maxAttempts after this many failures an event is dead-lettered (FAILED)
 */
@Validated
@ConfigurationProperties("app.outbox")
public record OutboxProperties(@Min(1) int batchSize, @Min(1) int maxAttempts) {
}
