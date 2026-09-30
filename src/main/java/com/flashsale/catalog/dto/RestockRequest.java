package com.flashsale.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RestockRequest(@Schema(example = "100") @Min(1) @Max(1_000_000) int quantity) {
}
