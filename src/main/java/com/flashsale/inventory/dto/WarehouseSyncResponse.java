package com.flashsale.inventory.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record WarehouseSyncResponse(List<Result> results) {

    /**
     * @param status APPLIED (stock changed now), DUPLICATE (already received — original outcome in {@code detail}),
     *               REJECTED (not applied: unknown SKU / would make available stock negative)
     */
    public record Result(String eventId, @Schema(example = "APPLIED") String status, String detail) {
    }
}
