package com.flashsale.flashsale.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** A slot with every seller's items, grouped by seller (platform view). */
public record AdminSessionView(
        @Schema(example = "55") long sessionId,
        @Schema(example = "12:00 – 13:00") String name,
        Instant startAt,
        Instant endAt,
        @Schema(example = "SCHEDULED") String status,
        @Schema(description = "true = generated from the region config") boolean generated,
        List<SellerGroup> sellers) {

    public record SellerGroup(@Schema(example = "2") long sellerId, List<Item> items) {
    }

    public record Item(long itemId, long productId, String sku, String productName, Long ruleId,
                       BigDecimal salePrice, int quota, int sold, String status) {
    }
}
