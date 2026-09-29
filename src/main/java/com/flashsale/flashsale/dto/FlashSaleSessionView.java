package com.flashsale.flashsale.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record FlashSaleSessionView(
        @Schema(example = "5") long sessionId,
        @Schema(example = "12:00 – 16:00") String name,
        Instant startAt,
        Instant endAt,
        List<FlashSaleItemView> items) {
}
