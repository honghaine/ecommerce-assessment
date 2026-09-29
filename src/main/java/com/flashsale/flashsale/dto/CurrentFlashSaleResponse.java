package com.flashsale.flashsale.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** All times are UTC (ISO-8601); render in the region's timezone on the client. */
public record CurrentFlashSaleResponse(
        Instant serverTime,
        @Schema(example = "VN") String region,
        @Schema(example = "VND") String currency,
        List<FlashSaleSessionView> sessions) {
}
