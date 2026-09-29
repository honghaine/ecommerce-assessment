package com.flashsale.flashsale.config;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param currentCacheTtl   TTL of the cached "live now" listing per region
 * @param reconcileHorizon  slots starting within this horizon get their Redis stock pre-warmed
 */
@Validated
@ConfigurationProperties("app.flash-sale")
public record FlashSaleProperties(@NotNull Duration currentCacheTtl, @NotNull Duration reconcileHorizon) {
}
