package com.flashsale.flashsale.dto;

import java.time.DayOfWeek;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Changes apply to slots generated from now on; already generated slots are kept. */
public record FlashSaleConfigRequest(
        @Schema(example = "true") boolean enabled,
        @Schema(description = "Window length in minutes; must divide 24h", example = "60")
        @Min(15) @Max(1440) int slotMinutes,
        @Schema(example = "[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"]")
        @NotNull Set<DayOfWeek> activeDays,
        @Schema(example = "2") @Min(1) @Max(14) int horizonDays) {
}
