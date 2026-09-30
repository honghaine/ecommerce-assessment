package com.flashsale.flashsale.model;

/**
 * @param skippedNoStock rule occurrences skipped because the product lacked available stock
 */
public record GenerationResult(String region, int slotsCreated, int itemsCreated, int skippedNoStock) {

    public static GenerationResult disabled(String region) {
        return new GenerationResult(region, 0, 0, 0);
    }
}
