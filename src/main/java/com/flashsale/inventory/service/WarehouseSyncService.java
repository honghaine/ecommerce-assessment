package com.flashsale.inventory.service;

import com.flashsale.inventory.dto.WarehouseSyncRequest;
import com.flashsale.inventory.dto.WarehouseSyncResponse;

/** Applies external warehouse stock deltas, each at most once per (source, eventId). */
public interface WarehouseSyncService {

    WarehouseSyncResponse apply(WarehouseSyncRequest request);
}
