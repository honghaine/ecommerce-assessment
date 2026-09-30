package com.flashsale.catalog.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @Schema(description = "Unique per region", example = "VN-HEADPHONE-001")
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String sku,
        @Schema(example = "Noise-cancelling Headphones") @NotBlank @Size(max = 255) String name,
        @Schema(example = "Over-ear, 30h battery") @Size(max = 2000) String description,
        @Schema(description = "Regular price in the region's currency", example = "4000000")
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal price,
        @Schema(description = "Initial stock", example = "500") @Min(0) @Max(1_000_000) int stock) {
}
