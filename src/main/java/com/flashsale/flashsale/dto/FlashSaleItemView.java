package com.flashsale.flashsale.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

public record FlashSaleItemView(
        @Schema(description = "Use this id to purchase", example = "12") long itemId,
        @Schema(example = "3") long productId,
        @Schema(example = "VN-PHONE-001") String sku,
        @Schema(example = "Smartphone X") String name,
        @Schema(description = "Regular price", example = "10000000.00") BigDecimal originalPrice,
        @Schema(description = "Flash-sale price", example = "5000000.00") BigDecimal amount,
        @Schema(example = "20") int quota,
        @Schema(example = "7") int remaining) {
}
