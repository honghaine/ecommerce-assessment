package com.flashsale.inventory.service;

import com.flashsale.inventory.dto.InventoryAuditResponse;

public interface InventoryAuditService {

    /** Compares every product's stock with its movement ledger and reports outbox lag. Read-only. */
    InventoryAuditResponse audit(String region);
}
