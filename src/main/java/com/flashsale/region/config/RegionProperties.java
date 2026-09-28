package com.flashsale.region.config;

import java.time.ZoneId;
import java.util.Currency;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Market metadata: {@code app.regions.VN.timezone=Asia/Ho_Chi_Minh}.
 * Single source of timezone for all business rules.
 */
@Validated
@ConfigurationProperties("app")
public record RegionProperties(@NotEmpty Map<String, Region> regions) {

    public record Region(@NotNull ZoneId timezone, @NotNull Currency currency) {
    }
}
