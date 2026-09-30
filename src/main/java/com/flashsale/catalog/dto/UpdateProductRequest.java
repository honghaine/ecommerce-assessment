package com.flashsale.catalog.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import com.flashsale.catalog.entity.ProductStatus;

/** Partial update: omitted (null) fields are unchanged. There is no delete — deactivate instead (keeps history). */
public record UpdateProductRequest(
        @Schema(example = "Noise-cancelling Headphones v2") @Size(min = 1, max = 255) String name,
        @Size(max = 2000) String description,
        @Schema(example = "3500000") @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal price,
        @Schema(description = "INACTIVE stops purchases and pauses its flash-sale rules", example = "INACTIVE")
        ProductStatus status) {
}
