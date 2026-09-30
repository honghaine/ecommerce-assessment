package com.flashsale.inventory.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Stock deltas pushed by an external warehouse system (WMS). Deltas — not absolute snapshots — so events can be
 * applied in any order and never overwrite units already sold through flash sales.
 */
public record WarehouseSyncRequest(
        @Schema(description = "Sending system", example = "WMS-HCM")
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String source,
        @NotEmpty @Size(max = 500) List<@Valid @NotNull StockEvent> events) {

    public record StockEvent(
            @Schema(description = "Unique per source; re-sending the same id is a no-op", example = "rcv-2026-09-30-0001")
            @NotBlank @Size(max = 100) String eventId,
            @Schema(example = "VN") @NotBlank @Size(max = 8) String region,
            @Schema(example = "VN-PHONE-001") @NotBlank @Size(max = 64) String sku,
            @Schema(description = "Units added (+) or removed (−)", example = "50")
            @Min(-1_000_000) @Max(1_000_000) int delta,
            @Schema(example = "RECEIVED", allowableValues = {"RECEIVED", "DAMAGED", "RETURNED", "CORRECTION"})
            @NotBlank @Pattern(regexp = "RECEIVED|DAMAGED|RETURNED|CORRECTION") String reason) {
    }
}
