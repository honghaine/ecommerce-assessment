package com.flashsale.flashsale.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** A concrete occurrence of a product in a slot (usually generated from a rule). */
public record SellerItemResponse(
        @Schema(example = "120") long itemId,
        @Schema(example = "55") long sessionId,
        @Schema(example = "12:00 – 13:00") String slotName,
        Instant startAt,
        Instant endAt,
        @Schema(example = "3") long productId,
        @Schema(description = "Rule that generated it (null if manual)", example = "4") Long ruleId,
        @Schema(example = "3000000.00") BigDecimal salePrice,
        @Schema(example = "20") int quota,
        @Schema(example = "4") int sold,
        @Schema(example = "APPROVED") String status) {
}
