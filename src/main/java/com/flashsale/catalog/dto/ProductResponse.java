package com.flashsale.catalog.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param available sellable stock not allocated to flash sales
 * @param reserved  stock allocated to flash-sale quotas
 */
public record ProductResponse(
        @Schema(example = "7") long id,
        @Schema(example = "VN") String region,
        @Schema(example = "VN-HEADPHONE-001") String sku,
        String name,
        String description,
        @Schema(example = "4000000.00") BigDecimal price,
        @Schema(example = "VND") String currency,
        @Schema(example = "ACTIVE") String status,
        @Schema(example = "500") int total,
        @Schema(example = "480") int available,
        @Schema(example = "20") int reserved) {
}
