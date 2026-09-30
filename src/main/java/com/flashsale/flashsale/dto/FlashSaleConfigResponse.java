package com.flashsale.flashsale.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

import com.flashsale.flashsale.entity.FlashSaleConfig;
import com.flashsale.flashsale.support.DaysOfWeek;

public record FlashSaleConfigResponse(
        @Schema(example = "VN") String region,
        @Schema(example = "Asia/Ho_Chi_Minh") String timezone,
        @Schema(example = "true") boolean enabled,
        @Schema(description = "Window length", example = "60") int slotMinutes,
        @Schema(description = "Windows per active day", example = "24") int windowsPerDay,
        Set<DayOfWeek> activeDays,
        @Schema(description = "Days of slots generated ahead (incl. today)", example = "2") int horizonDays,
        Instant updatedAt) {

    public static FlashSaleConfigResponse from(FlashSaleConfig config, String timezone) {
        return new FlashSaleConfigResponse(config.getRegion(), timezone, config.isEnabled(), config.getSlotMinutes(),
                config.windowsPerDay(), DaysOfWeek.fromMask(config.getActiveDays()), config.getHorizonDays(),
                config.getUpdatedAt());
    }
}
