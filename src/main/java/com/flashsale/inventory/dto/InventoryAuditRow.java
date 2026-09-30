package com.flashsale.inventory.dto;

/**
 * Stock vs. what the movement ledger says it should be.
 * {@code ledgerTotal = Σ RESTOCK/ADJUST/WAREHOUSE_SYNC/PURCHASE}, {@code ledgerAvailable = Σ RESTOCK/ADJUST/
 * WAREHOUSE_SYNC/RESERVE/RELEASE}. {@code consistent = false} means drift that needs investigation.
 */
public record InventoryAuditRow(long productId, String sku, int total, int available, int reserved,
                                long ledgerTotal, long ledgerAvailable, boolean consistent) {
}
