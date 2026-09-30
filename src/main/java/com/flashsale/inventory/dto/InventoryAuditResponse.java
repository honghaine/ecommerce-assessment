package com.flashsale.inventory.dto;

import java.time.Instant;
import java.util.List;

/**
 * @param pendingEvents stock effects not applied yet (normally drained within a second)
 * @param failedEvents  dead-lettered events — need attention
 */
public record InventoryAuditResponse(Instant checkedAt, String region, long pendingEvents, long failedEvents,
                                     boolean allConsistent, List<InventoryAuditRow> products) {
}
