package com.flashsale.inventory.service;

import java.util.UUID;

public interface InventoryService {

    /** Opens stock for a new product. */
    void open(long productId, String region, int quantity);

    /**
     * Allocates stock to a flash-sale quota ({@code available → reserved}).
     *
     * @return false if not enough available stock
     */
    boolean reserve(long productId, String region, int quantity);

    void restock(long productId, String region, int quantity);

    /**
     * Client-driven restock, applied at most once per {@code idempotencyKey}.
     *
     * @return false if this key was already applied
     */
    boolean restock(long productId, String region, int quantity, String idempotencyKey);

    /**
     * Flash-sale slot closed for one item: sold units leave the stock, unsold units return to available.
     * Stock is not touched while the slot runs — this is the only place a flash-sale sale reaches inventory.
     */
    void settleFlashSaleItem(long itemId, long productId, String region, int quota, int sold, UUID eventId);

    /** @return false if the delta would make available stock negative */
    boolean applyWarehouseDelta(long productId, String region, int delta);

    /** Returns unsold flash-sale quota ({@code reserved → available}). */
    void release(long productId, String region, int quantity);
}
