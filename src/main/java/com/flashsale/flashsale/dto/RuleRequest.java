package com.flashsale.flashsale.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Recurring flash-sale plan for one of the seller's products.
 *
 * @param slotStartTime region-local window start; must be a window boundary of the region config
 *                      (with the default 60-minute windows: 00:00, 01:00, … 23:00)
 */
public record RuleRequest(
        @Schema(description = "Own product id", example = "3") @NotNull Long productId,
        @Schema(description = "Region-local window start (HH:mm)", example = "12:00", type = "string")
        @NotNull LocalTime slotStartTime,
        @Schema(description = "Weekdays the rule applies", example = "[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]")
        @NotEmpty Set<DayOfWeek> daysOfWeek,
        @Schema(description = "Flash-sale price (below the product price)", example = "3000000")
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal salePrice,
        @Schema(description = "Units available in each slot", example = "20") @Min(1) @Max(100_000) int quota) {
}
