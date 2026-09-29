package com.flashsale.seed;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Demo data (dev/review only).
 *
 * @param enabled  seed on startup and keep today/tomorrow slots topped up
 * @param password password of every seeded account
 * @param buyers   number of demo buyers per region ({@code buyer0001@demo.flashsale.dev}, ...)
 * @param regions  regions to seed
 * @param buyerBalance wallet balance of demo buyers (enough for any demo flash-sale item)
 */
@Validated
@ConfigurationProperties("app.seed")
public record SeedProperties(boolean enabled, @NotBlank String password, @Min(0) int buyers, List<String> regions,
                             BigDecimal buyerBalance) {

    public SeedProperties {
        regions = regions == null || regions.isEmpty() ? List.of("VN") : List.copyOf(regions);
        buyerBalance = buyerBalance == null ? new BigDecimal("100000000") : buyerBalance;
    }
}
