package com.flashsale.flashsale.service;

import com.flashsale.flashsale.model.PurchaseResult;

public interface PurchaseService {

    /**
     * Buys one unit of a flash-sale item for the current buyer.
     * Idempotent per {@code (userId, idempotencyKey)}.
     */
    PurchaseResult purchase(long userId, String userRegion, long itemId, String idempotencyKey);
}
