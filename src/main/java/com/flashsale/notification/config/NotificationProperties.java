package com.flashsale.notification.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param dispatchEnabled   run the scheduled dispatcher on this instance
 * @param batchSize         messages per dispatch run
 * @param mockLogContent    mock sender logs the message content (e.g. OTP) — dev only
 */
@Validated
@ConfigurationProperties("app.notification")
public record NotificationProperties(boolean dispatchEnabled, @Min(1) int batchSize, boolean mockLogContent) {
}
